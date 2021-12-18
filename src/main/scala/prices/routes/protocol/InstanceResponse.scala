package prices.routes.protocol

import io.circe._
import io.circe.syntax._

import prices.data._

final case class InstanceResponse(value: Instance)

object InstanceResponse {

  implicit val encoder: Encoder[InstanceResponse] =
    Encoder.instance[InstanceResponse] {
      case InstanceResponse(instance) =>
        InstanceKindResponse(instance.kind).asJson.deepMerge(InstancePriceResponse(instance.price).asJson)
    }
}
