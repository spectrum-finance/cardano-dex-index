package fi.spectrumlabs.core.network.models

import cats.MonadError
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT

@derive(encoder, decoder)
sealed trait HttpError

object HttpError {
  final case class NotFound(what: String) extends HttpError
  final case class Unknown(code: Int, msg: String) extends HttpError
  case object NoContent extends HttpError

  implicit def adaptThrowable[F[_]](implicit
    F: MonadError[F, Throwable]
  ): AdaptThrowableEitherT[F, HttpError] =
    new AdaptThrowableEitherT[F, HttpError] {

      final def adapter: Throwable => F[HttpError] = e => F.pure(Unknown(500, e.getMessage))
    }
}
