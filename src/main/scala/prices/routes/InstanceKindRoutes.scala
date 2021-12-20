package prices.routes

import cats.implicits._
import cats.effect._
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import prices.routes.protocol._
import prices.services.InstanceKindService
import prices.services.InstanceKindService.Exception.{ APICallFailure, TooManyRequestsFailure, InvalidResponse }
import org.log4s.getLogger

final case class InstanceKindRoutes[F[_]: Sync](instanceKindService: InstanceKindService[F]) extends Http4sDsl[F] {
  val logger = getLogger
  val prefix = "/instance-kinds"

  implicit val instanceKindResponseEncoder = jsonEncoderOf[F, List[InstanceKindResponse]]
  implicit val errorResponseEncoder = jsonEncoderOf[F, ErrorResponse]

  private val get: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root =>
      instanceKindService.getAll()
        .flatMap(kinds => Ok(kinds.map(k => InstanceKindResponse(k))))
        .handleErrorWith{
          case ex : InstanceKindService.Exception =>
            ex match {
              case APICallFailure(message) =>
                logger.error(message)
                InternalServerError(ErrorResponse("APICallFailure", "Internal server error"))
              case TooManyRequestsFailure(message) =>
                logger.error(message)
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
    Router(
      prefix -> get
    )

}
