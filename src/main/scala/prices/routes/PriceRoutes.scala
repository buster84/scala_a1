package prices.routes

import cats.implicits._
import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import prices.data.InstanceKind
import prices.routes.protocol._
import prices.services.PriceService
import prices.services.PriceService.Exception.{APICallFailure, InvalidResponse, TooManyRequestsFailure}
import org.log4s.getLogger

import scala.util.control.NoStackTrace

final case class PriceRoutes[F[_]: Sync](priceService: PriceService[F]) extends Http4sDsl[F] {
  val logger = getLogger
  val instanceKindsPath = "/instance-kinds"
  val pricePath = "/prices"

  implicit val instanceKindResponseEncoder = jsonEncoderOf[F, List[InstanceKindResponse]]
  implicit val instanceResponseEncoder = jsonEncoderOf[F, List[InstanceResponse]]
  implicit val errorResponseEncoder = jsonEncoderOf[F, ErrorResponse]

  private val getInstanceKinds: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root =>
      priceService.getAllInstanceKinds()
        .flatMap(kinds => Ok(kinds.map(k => InstanceKindResponse(k))))
  }

  case class InvalidKindQuery(message: String) extends NoStackTrace

  private def validateKinds(kinds: List[InstanceKind]): F[Either[Throwable, List[InstanceKind]]] = {
    priceService.getAllInstanceKinds().map { allKinds =>
      val allKindsSet = allKinds.toSet

      if (kinds.forall(p => allKindsSet.contains(p))) {
        Right(kinds)
      } else {
        Left(InvalidKindQuery("Invalid kind exists"))
      }
    }
  }

  private val getPrices: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root :? KindQueryParamMatcher(maybeKinds) =>
      val kinds: F[Either[Throwable, List[InstanceKind]]] = maybeKinds match {
        case None => priceService.getAllInstanceKinds().map(Right(_))
        case Some(kinds) => validateKinds(kinds)
      }
      kinds
        .flatMap{
          case Left(_) => {
            BadRequest(ErrorResponse("InvalidRequest", "Bad request"))
          }
          case Right(k) => {
            priceService.getAllPrices(k)
              .flatMap(instances => Ok(instances.map(i => InstanceResponse(i))))
          }
        }
  }


  private def errorHandle(e: Throwable) = {
    e match {
      case ex : PriceService.Exception =>
        ex match {
          case APICallFailure(message) =>
            logger.warn(message)
            InternalServerError(ErrorResponse("APICallFailure", "Internal server error"))
          case TooManyRequestsFailure(message) =>
            logger.warn(message)
            TooManyRequests(ErrorResponse("TooManyRequestsFailure", "Too much requests"))
          case InvalidResponse(message) =>
            logger.error(message)
            InternalServerError(ErrorResponse("InvalidResponse", "Internal server error"))
        }
      case other =>
        logger.error(other.getMessage())
        InternalServerError(ErrorResponse("Other", "Internal server error"))
    }
  }

  def routes: HttpRoutes[F] =
    RoutesHttpErrorHandler(
      Router(
        instanceKindsPath -> getInstanceKinds,
        pricePath -> getPrices
      )
    )(errorHandle)
}
