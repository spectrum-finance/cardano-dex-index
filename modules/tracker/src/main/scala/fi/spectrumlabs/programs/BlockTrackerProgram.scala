package fi.spectrumlabs.programs

import cats.effect.Timer
import cats.syntax.foldable._
import cats.{Defer, Foldable, Functor, FunctorFilter, Monad, SemigroupK}
import fi.spectrumlabs.config.TrackerConfig
import fi.spectrumlabs.core.models.Block
import fi.spectrumlabs.core.streaming.{Producer, Record}
import fi.spectrumlabs.repositories.TrackerCache
import fi.spectrumlabs.services.Explorer
import mouse.any._
import tofu.Catches
import tofu.logging.{Logging, Logs}
import tofu.streams.{Compile, Evals, Pace, Temporal}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.handle._
import tofu.syntax.streams.combineK._
import tofu.syntax.streams.compile._
import tofu.syntax.streams.emits._
import tofu.syntax.streams.evals._
import tofu.syntax.streams.pace._
import tofu.syntax.streams.temporal._

trait BlockTrackerProgram[S[_]] {
  def run: S[Unit]
}

object BlockTrackerProgram {

  def create[
    S[_]: Monad: Evals[*[_], F]: FunctorFilter: Temporal[*[_], C]: Compile[*[_], F]: SemigroupK: Defer: Pace,
    F[_]: Monad: Timer: Catches,
    I[_]: Functor,
    C[_]: Foldable
  ](producer: Producer[String, Block, S], config: TrackerConfig)(implicit
    cache: TrackerCache[F],
    explorer: Explorer[S, F],
    logs: Logs[I, F]
  ): I[BlockTrackerProgram[S]] =
    logs.forService[BlockTrackerProgram[S]].map(implicit __ => new Impl[S, F, C](producer, config))

  private final class Impl[
    S[_]: Monad: Evals[*[_], F]: FunctorFilter: Temporal[*[_], C]: Compile[*[_], F]: SemigroupK: Defer: Pace,
    F[_]: Monad: Logging: Catches: Timer,
    C[_]: Foldable
  ](producer: Producer[String, Block, S], config: TrackerConfig)(implicit
    cache: TrackerCache[F],
    explorer: Explorer[S, F]
  ) extends BlockTrackerProgram[S] {

    def run: S[Unit] =
      (eval(cache.getLastBlockOffset) >>= { lastOffset: Long =>
        val offset = lastOffset max config.initialOffset
        eval(info"Current offset is: $offset. Going to perform next request.") >>
        explorer
          .streamBlocks(offset, config.limit)
          .groupWithin(config.batchSize, config.timeout)
          .evalMap { batch =>
            info"Received batch of ${batch.size} elems."
              .as {
                batch.toList.map(Block.fromExplorer).map(block => Record(block.height.toString, block))
              }
              .flatMap { blocks =>
                (emits[S](blocks) |> producer.produce).drain
              }
              .flatMap { _ =>
                cache.setLastBlockOffset(batch.size + offset)
              }
              .flatMap { _ =>
                if (batch.size < config.limit)
                  debug"Batch size is less than ${config.limit}. Going to sleep for ${config.throttleRate}" >>
                  Timer[F].sleep(config.throttleRate)
                else debug"Batch size equals ${config.limit}. Going to request next batch"
              }
              .handleWith { err: Throwable =>
                error"The error ${err.getMessage} occurred in tracker stream."
              }
          }
      }).repeat
  }
}