package prices.services

import scala.util.control.NoStackTrace

import prices.data._

trait PriceService[F[_]] {
  def getAllInstanceKinds(): F[List[InstanceKind]]
  def getAllPrices(kinds: List[InstanceKind]): F[List[Instance]]
  def getAllPrices(): F[List[Instance]]
}

object PriceService {

  sealed trait Exception extends NoStackTrace
  object Exception {
    case class APICallFailure(message: String) extends Exception
    case class InvalidResponse(message: String) extends Exception
    case class TooManyRequestsFailure(message: String) extends Exception
  }

}
