package prices.routes.protocol

import io.circe._
import io.circe.syntax._

final case class ErrorResponse(code: String, message: String)
object ErrorResponse {

  implicit val encoder: Encoder[ErrorResponse] =
    Encoder.instance[ErrorResponse] {
      case ErrorResponse(code, message) =>
        Json.obj(
          "code" -> code.asJson,
          "message" -> message.asJson
        )
    }

}
