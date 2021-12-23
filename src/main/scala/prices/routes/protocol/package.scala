package prices.routes

import cats.data.{ Kleisli, OptionT }

import cats.ApplicativeError
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps}
import org.http4s.{HttpRoutes, QueryParamDecoder, Response}
import org.http4s.dsl.io.OptionalQueryParamDecoderMatcher
import prices.data._

package object protocol {

  implicit val instanceKindsQueryParamDecoder: QueryParamDecoder[List[InstanceKind]] =
    QueryParamDecoder[String].map(kinds => kinds.split(",").map(InstanceKind).toSet.toList)

  object KindQueryParamMatcher extends OptionalQueryParamDecoderMatcher[List[InstanceKind]]("kind")

  object RoutesHttpErrorHandler {
    def apply[F[_]: ApplicativeError[*[_], E], E <: Throwable](routes: HttpRoutes[F])(handler: E => F[Response[F]]): HttpRoutes[F] =
      Kleisli { req =>
        OptionT {
          routes.run(req).value.handleErrorWith(e => handler(e).map(Option(_)))
        }
      }
  }
}
