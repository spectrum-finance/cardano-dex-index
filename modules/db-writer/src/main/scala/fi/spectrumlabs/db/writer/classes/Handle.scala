package fi.spectrumlabs.db.writer.classes

import cats.data.NonEmptyList
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Functor, Monad}
import fi.spectrumlabs.db.writer.persistence.Persist
import mouse.any._
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._

/** Keeps both ToSchema from A to B and Persist for B.
  * Contains evidence that A can be mapped into B and B can be persisted.
  *
  * Takes batch of T elements, maps them using ToSchema, persists them using Persist
  */

trait Handle[T, F[_]] {
  def handle(in: NonEmptyList[T]): F[Unit]
}

object Handle {

  def createOne[A, B, I[_]: Functor, F[_]: Monad](
    persist: Persist[B, F],
    handleLogName: String
  )(implicit toSchema: ToSchema[A, B], logs: Logs[I, F]): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOne[A, B, F](persist, handleLogName))

  def createList[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, List[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplList[A, B, F](persist, handleLogName))

  def createNel[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, NonEmptyList[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplNel[A, B, F](persist, handleLogName))

  def createOption[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, Option[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOption[A, B, F](persist, handleLogName))

  private final class ImplOne[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, B]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      (in.map(toSchema(_)) |> persist.persist)
        .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")

  }

  private final class ImplList[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, List[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.toList.flatMap(toSchema(_)) match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist. ${in.toList.toString()}"
      }
  }

  private final class ImplNel[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, NonEmptyList[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.flatMap(toSchema(_)).toList match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  private final class ImplOption[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, Option[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.map(toSchema(_)).toList.flatten match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist."
      }
  }
}
