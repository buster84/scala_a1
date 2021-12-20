package prices.services

import scala.util.control.NoStackTrace

import prices.data._

trait InstanceKindService[F[_]] {
  def getAll(): F[List[InstanceKind]]
}

object InstanceKindService {

  sealed trait Exception extends NoStackTrace
  object Exception {
    case class APICallFailure(message: String) extends Exception
    case class InvalidResponse(message: String) extends Exception
    case class TooManyRequestsFailure(message: String) extends Exception
  }

}
