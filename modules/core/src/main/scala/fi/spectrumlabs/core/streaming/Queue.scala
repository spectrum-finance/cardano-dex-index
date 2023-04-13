package fi.spectrumlabs.core.streaming

import cats.effect.{Concurrent, Sync}
import cats.tagless.InvariantK
import cats.{~>, FlatMap}
import fs2.{concurrent, Pipe, Stream}
import tofu.lift.IsoK
import tofu.logging.{Logging, Logs}
import tofu.syntax.monadic._

trait Queue[F[_], A] {

  /** semantic blocking */
  def take: F[A]

  /** semantic non blocking */
  def tryTake: F[Option[A]]

  def offer(a: A): F[Unit]
}

trait QueueStreaming[S[_], F[_], A] extends Queue[F, A] {
  def enqueue: S[A] => S[Unit]
  def dequeueBatch(size: Int): F[List[A]]
}

object Queue {

  implicit def invK[Eff[_], A]: InvariantK[QueueStreaming[*[_], Eff, A]] =
    new InvariantK[QueueStreaming[*[_], Eff, A]] {

      def imapK[F[_], G[_]](af: QueueStreaming[F, Eff, A])(fk: F ~> G)(gK: G ~> F): QueueStreaming[G, Eff, A] =
        new QueueStreaming[G, Eff, A] {
          def enqueue: G[A] => G[Unit] = ga => fk(af.enqueue(gK(ga)))

          def dequeueBatch(size: Int): Eff[List[A]] = af.dequeueBatch(size)

          def take: Eff[A] = af.take

          def tryTake: Eff[Option[A]] = af.tryTake

          def offer(a: A): Eff[Unit] = af.offer(a)
        }
    }

  def create[I[_]: Sync, F[_], G[_]: Concurrent, A](boundSize: Int)(implicit
    logs: Logs[I, G],
    isoKFG: IsoK[F, Stream[G, *]]
  ): I[QueueStreaming[F, G, A]] =
    logs
      .forService[QueueStreaming[F, G, A]]
      .flatMap(implicit __ =>
        concurrent.Queue
          .in[I]
          .bounded[G, A](boundSize)
          .map { queue: concurrent.Queue[G, A] =>
            new ImplStreaming[G, A](queue)
          }
          .map { queue: QueueStreaming[Stream[G, *], G, A] =>
            implicitly[InvariantK[QueueStreaming[*[_], G, A]]].imapK(queue)(isoKFG.fromF)(isoKFG.tof)
          }
      )

  final private class ImplStreaming[F[_]: Logging: FlatMap, A](queue: fs2.concurrent.Queue[F, A])
    extends QueueStreaming[Stream[F, *], F, A] {

    def dequeueBatch(size: Int): F[List[A]] =
      queue
        .tryDequeueChunk1(size)
        .map {
          case None        => List.empty
          case Some(chunk) => chunk.toList
        }

    def enqueue: Pipe[F, A, Unit] = queue.enqueue

    def take: F[A] = queue.dequeue1

    def tryTake: F[Option[A]] = queue.tryDequeue1

    def offer(a: A): F[Unit] = queue.enqueue1(a)
  }
}
