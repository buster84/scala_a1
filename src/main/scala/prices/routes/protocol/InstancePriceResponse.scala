package prices.routes.protocol

import io.circe._
import io.circe.syntax._

import prices.data._

final case class InstancePriceResponse(value: InstancePrice)

object InstancePriceResponse {

  implicit val encoder: Encoder[InstancePriceResponse] =
    Encoder.instance[InstancePriceResponse] {
      case InstancePriceResponse(k) =>
        Json.obj(
          "price" -> k.getValue.asJson
        )
    }

}
