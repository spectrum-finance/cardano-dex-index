package fi.spectrumlabs.core.network

import cats.{Applicative, Monad}
import cats.syntax.either._
import fi.spectrumlabs.core.network.models.HttpError
import sttp.client3.{Response, ResponseException}
import tofu.{Catches, Throws}
import tofu.syntax.handle._
import tofu.syntax.monadic._
import tofu.syntax.raise._

object syntax {

  implicit class AdaptThrowableOps[F[_], G[_, _], E, A](val fa: F[A]) extends AnyVal {

    def adaptThrowable(implicit A: AdaptThrowable[F, G, E]): G[E, A] = A.adaptThrowable(fa)
  }

  implicit class ResponseOps[F[_], A, E](private val fr: F[Response[Either[ResponseException[String, E], A]]])
    extends AnyVal {

    def absorbError(implicit R: Throws[F], A: Monad[F]): F[A] =
      fr.flatMap(_.body.leftMap(resEx => new Throwable(resEx.getMessage)).toRaise)
  }

  implicit class PlainResponseOps[F[_], A](private val fr: F[Response[Either[String, A]]]) extends AnyVal {

    def absorbError(implicit R: Throws[F], A: Monad[F]): F[A] =
      fr.flatMap(_.body.leftMap(new Throwable(_)).toRaise)
  }

  implicit class ServiceOps[F[_], A](protected val fa: F[A]) extends AnyVal {

    def eject(implicit F: Applicative[F], C: Catches[F]): F[Either[HttpError, A]] =
      fa.map(_.asRight[HttpError]).handle[Throwable](e => HttpError.Unknown(500, e.getMessage).asLeft)
  }

  implicit class ServiceOptionOps[F[_], A](protected val fa: F[Option[A]]) extends AnyVal {

    def orNotFound(what: String)(implicit F: Applicative[F], C: Catches[F]): F[Either[HttpError, A]] =
      fa.map(_.fold[Either[HttpError, A]](HttpError.NotFound(what).asLeft)(_.asRight))
        .handle[Throwable](e => HttpError.Unknown(500, e.getMessage).asLeft)
  }
}
