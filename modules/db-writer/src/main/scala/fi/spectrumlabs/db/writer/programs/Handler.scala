package fi.spectrumlabs.db.writer.programs

import cats.data.NonEmptyList
import cats.syntax.foldable._
import cats.syntax.parallel._
import cats.{Foldable, Monad, Parallel}
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.db.writer.classes.Handle
import fi.spectrumlabs.db.writer.config.WriterConfig
import tofu.Catches
import tofu.logging.Logging
import tofu.streams.{Evals, Temporal}
import tofu.syntax.handle._
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.streams.evals._
import tofu.syntax.streams.temporal._

trait Handler[S[_]] {
  def handle: S[Unit]
}

object Handler {

  def create[
    A,
    S[_]: Monad: Evals[*[_], F]: Temporal[*[_], C]: Catches,
    F[_]: Monad: Parallel,
    C[_]: Foldable
  ](
    config: WriterConfig,
    handlerName: String
  )(implicit
    consumer: Consumer[_, Option[A], S, F],
    handlers: NonEmptyList[Handle[A, F]],
    logs: Logging.Make[F]
  ): Handler[S] =
    logs.forService[Handler[S]].map(implicit __ => new Impl[A, S, F, C](config, handlerName))

  final private class Impl[
    A,
    S[_]: Monad: Evals[*[_], F]: Temporal[*[_], C]: Catches,
    F[_]: Monad: Parallel: Logging,
    C[_]: Foldable
  ](config: WriterConfig, name: String)(implicit
    consumer: Consumer[_, Option[A], S, F],
    handlers: NonEmptyList[Handle[A, F]]
  ) extends Handler[S] {

    def handle: S[Unit] =
      consumer.stream
        .groupWithin(config.batchSize, config.timeout)
        .flatMap { batch =>
          batch.toList.flatMap(_.message) match {
            case x :: xs =>
              val nel = NonEmptyList.of(x, xs: _*)
              Evals[S, F].eval(info"$name: going to test: ${nel.toString()}") >>
              eval(handlers.toList.parTraverse(_.handle(nel)))
                .evalMap(_ => info"Handler [$name] processed batch of ${nel.size} elements.")
                .evalMap(_ => batch.toList.lastOption.fold(().pure[F])(_.commit))
                .handleWith { err: Throwable =>
                  eval(info"Handler [$name] got error: ${err.getMessage}. Failed to insert ${nel.size} elements.")
                }
            case Nil =>
              eval(info"Handler [$name] got empty batch. Skip iteration.")
          }
        }
        .handleWith { err: Throwable =>
          eval(info"Handler [$name] got error: ${err.getMessage}. It is gonna die and then restart.") >> handle
        }
  }
}
