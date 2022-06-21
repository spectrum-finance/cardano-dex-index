package fi.spectrumlabs.core.syntax.sttp

import cats.Monad
import cats.syntax.either._
import tofu.Throws
import tofu.syntax.monadic._
import tofu.syntax.raise._
import sttp.client3.{Response, ResponseException}

object syntax {

  implicit class ResponseOps[F[_], A, E](private val fr: F[Response[Either[ResponseException[String, E], A]]])
    extends AnyVal {

    def absorbError(implicit R: Throws[F], A: Monad[F]): F[A] =
      fr.flatMap(_.body.leftMap(resEx => new Throwable(resEx.getMessage)).toRaise)
  }

  implicit class PlainResponseOps[F[_], A](private val fr: F[Response[Either[String, A]]])
    extends AnyVal {

    def absorbError(implicit R: Throws[F], A: Monad[F]): F[A] =
      fr.flatMap(_.body.leftMap(new Throwable(_)).toRaise)
  }
}