package fi.spectrumlabs.http.cache

import cats.{Foldable, Functor, Monad}
import fi.spectrumlabs.core.models.Block
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.core.streaming.syntax.CommittableOps
import tofu.streams.{Chunks, Evals, Temporal}
import tofu.syntax.streams.all.toEvalsOps
import scala.concurrent.duration._

trait HttpCacheInvalidator[F[_]] {
  def run: F[Unit]
}

object HttpCacheInvalidator {

  val BatchSize          = 128
  val BatchCommitTimeout = 1.second

  def make[
    S[_]: Evals[*[_], F]: Chunks[*[_], C]: Temporal[*[_], C],
    F[_]: Monad,
    C[_]: Functor: Foldable
  ](implicit
    caching: HttpResponseCaching[F],
    blocks: Consumer[String, Block, S, F]
  ): HttpCacheInvalidator[S] =
    new Invalidator[S, F, C](caching, blocks)

  final class Invalidator[
    S[_]: Evals[*[_], F]: Chunks[*[_], C]: Temporal[*[_], C],
    F[_]: Monad,
    C[_]: Functor: Foldable
  ](
    caching: HttpResponseCaching[F],
    blocks: Consumer[String, Block, S, F]
  ) extends HttpCacheInvalidator[S] {

    def run: S[Unit] =
      blocks.stream
        .evalTap(_ => caching.invalidateAll)
        .commitBatchWithin(BatchSize, BatchCommitTimeout)
  }
}
