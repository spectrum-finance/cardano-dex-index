package fi.spectrumlabs.core.http.cache

import cats.{Foldable, Functor, Monad}
import fi.spectrumlabs.core.models.Block
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.core.streaming.syntax.CommittableOps
import tofu.streams.{Chunks, Evals, Temporal}
import tofu.syntax.streams.all.toEvalsOps

trait HttpCacheInvalidator[F[_]] {
  def run: F[Unit]
}

object HttpCacheInvalidator {

  def make[
    S[_]: Evals[*[_], F]: Chunks[*[_], C]: Temporal[*[_], C],
    F[_]: Monad,
    C[_]: Functor: Foldable
  ](config: HttpCacheConfig)(implicit
    caching: HttpResponseCaching[F],
    blocks: Consumer[String, Block, S, F]
  ): HttpCacheInvalidator[S] =
    new Invalidator[S, F, C](caching, blocks, config)

  final class Invalidator[
    S[_]: Evals[*[_], F]: Chunks[*[_], C]: Temporal[*[_], C],
    F[_]: Monad,
    C[_]: Functor: Foldable
  ](
    caching: HttpResponseCaching[F],
    blocks: Consumer[String, Block, S, F],
    config: HttpCacheConfig
  ) extends HttpCacheInvalidator[S] {

    def run: S[Unit] =
      blocks.stream
        .evalTap(_ => caching.invalidateAll)
        .commitBatchWithin(config.batchSize, config.batchCommitTimeout)
  }
}
