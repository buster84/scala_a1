package prices.services

import cats.ApplicativeError
import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import org.http4s.client.middleware.RetryPolicy
import org.http4s.ember.client.EmberClientBuilder
import prices.data._

import scala.concurrent.duration.{Duration, FiniteDuration}

object SmartcloudInstanceKindService {

  final case class Config(
      baseUri: String,
      token: String
  )

  def make[F[_]: Async](config: Config): InstanceKindService[F] = new SmartcloudInstanceKindService(config)

  private final class SmartcloudInstanceKindService[F[_]: Async](
      config: Config
  ) extends InstanceKindService[F] {

    implicit val instanceKindsEntityDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    val getAllUri = Request[F](Method.GET,
      Uri.fromString(s"${config.baseUri}/instances").getOrElse(throw new Exception("Invalid URL"))
    ).putHeaders(
      headers.Authorization(Credentials.Token(AuthScheme.Bearer, this.config.token)),
      headers.Accept(MediaType.application.json))

    val backoff = { count: Int => if (count < 3) Some(Duration.Zero) else None }

    override def getAll(): F[List[InstanceKind]] = {
      EmberClientBuilder
        .default[F]
        .withRetryPolicy(RetryPolicy(backoff))
        .build
        .use(client =>
          getRequest[List[String]](client, getAllUri)
            .map(l => l.map(InstanceKind(_)))
        )
    }

    def getRequest[A](client: Client[F], req: Request[F])(implicit entityDecoder: EntityDecoder[F, A], ae: ApplicativeError[F, Throwable]): F[A] =
      client.run(req).use {
        case Status.Successful(r) =>
          r.attemptAs[A].foldF({
            error => ae.raiseError(InstanceKindService.Exception.InvalidResponse(error.getMessage()))
          }, { value => ae.pure(value) })
        case r =>
          r.as[String]
          .flatMap{
            b =>
              if (r.status.code == 429) {
                ae.raiseError(InstanceKindService.Exception.TooManyRequestsFailure(s"Too many requests"))
              }else {
                ae.raiseError(InstanceKindService.Exception.APICallFailure(s"Request $req failed with status ${r.status.code} and body $b"))
              }
          }
      }
  }

}
