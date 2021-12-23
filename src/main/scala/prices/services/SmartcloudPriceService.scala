package prices.services

import cats.ApplicativeError
import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import org.http4s.client.middleware.RetryPolicy
import org.http4s.ember.client.EmberClientBuilder

import io.circe.generic.auto._

import prices.data._

import scala.concurrent.duration.Duration

object SmartcloudPriceService {

  final case class Config(
      baseUri: String,
      token: String
  )

  def make[F[_]: Async](config: Config): PriceService[F] = new SmartcloudPriceService(config)

  private final class SmartcloudPriceService[F[_]: Async](
      config: Config
  ) extends PriceService[F] {

    case class InstanceJson(kind: String, price: Double, timestamp: String)
    implicit val instanceKindsEntityDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]
    implicit val instanceEntityDecoder: EntityDecoder[F, Instance] = jsonOf[F, InstanceJson].map(i => Instance(InstanceKind(i.kind), InstancePrice(i.price)))

    val getAllInstanceKindsUri = Request[F](Method.GET,
      Uri.fromString(s"${config.baseUri}/instances").getOrElse(throw new Exception("Invalid URL"))
    ).putHeaders(
      headers.Authorization(Credentials.Token(AuthScheme.Bearer, this.config.token)),
      headers.Accept(MediaType.application.json))

    def priceUri(kind: InstanceKind) = Request[F](Method.GET,
      Uri.fromString(s"${config.baseUri}/instances/${kind.getString}").getOrElse(throw new Exception("Invalid URL"))
    ).putHeaders(
      headers.Authorization(Credentials.Token(AuthScheme.Bearer, this.config.token)),
      headers.Accept(MediaType.application.json))

    val backoff = { count: Int => if (count < 3) Some(Duration.Zero) else None }

    private def buildClient[A](fn: Client[F] => F[A]): F[A] = {
      EmberClientBuilder
        .default[F]
        .withRetryPolicy(RetryPolicy(backoff))
        .build
        .use(fn)
    }

    private def getAllInstanceKindsRequest = { client: Client[F] =>
      getRequest[List[String]](client, getAllInstanceKindsUri)
        .map(l => l.map(InstanceKind(_)))
    }
    override def getAllInstanceKinds(): F[List[InstanceKind]] = {
      buildClient(getAllInstanceKindsRequest)
    }

    def getAllPricesRequest(kinds: List[InstanceKind], client: Client[F]) = {
      kinds.map(kind => getRequest[Instance](client, priceUri(kind))).sequence
    }
    override def getAllPrices(kinds: List[InstanceKind]): F[List[Instance]] = {
      buildClient { client =>
        getAllPricesRequest(kinds, client)
      }
    }

    override def getAllPrices(): F[List[Instance]] = {
      buildClient { client =>
        getAllInstanceKindsRequest(client).flatMap(kinds => getAllPricesRequest(kinds, client))
      }
    }

    def getRequest[A](client: Client[F], req: Request[F])(implicit entityDecoder: EntityDecoder[F, A], ae: ApplicativeError[F, Throwable]): F[A] =
      client.run(req).use {
        case Status.Successful(r) =>
          r.attemptAs[A].foldF({
            error => ae.raiseError(PriceService.Exception.InvalidResponse(error.getMessage()))
          }, { value => ae.pure(value) })
        case r =>
          r.as[String]
          .flatMap{
            b =>
              if (r.status.code == 429) {
                ae.raiseError(PriceService.Exception.TooManyRequestsFailure(s"Too many requests"))
              }else {
                ae.raiseError(PriceService.Exception.APICallFailure(s"Request $req failed with status ${r.status.code} and body $b"))
              }
          }
      }
  }

}
