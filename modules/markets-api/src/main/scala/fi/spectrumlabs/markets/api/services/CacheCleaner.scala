package fi.spectrumlabs.markets.api.services

import cats.Monad
import cats.effect.Timer
import derevo.derive
import fi.spectrumlabs.core.http.cache.HttpResponseCaching
import tofu.higherKind.RepresentableK
import tofu.higherKind.derived.representableK
import tofu.streams.Evals
import tofu.syntax.monadic._
import tofu.syntax.streams.evals._

import scala.concurrent.duration.DurationInt

trait CacheCleaner[S[_]] {
  def clean: S[Unit]
}

object CacheCleaner {

  implicit def representableK: RepresentableK[CacheCleaner] =
    tofu.higherKind.derived.genRepresentableK

  def make[F[_]: Monad: Timer](c: HttpResponseCaching[F]): CacheCleaner[F] =
    new Live[F](c)

  final private class Live[F[_]: Monad: Timer](c: HttpResponseCaching[F]) extends CacheCleaner[F] {
    def clean: F[Unit] =
      Timer[F].sleep(10.seconds) >> c.invalidateAll >> clean
  }
}
