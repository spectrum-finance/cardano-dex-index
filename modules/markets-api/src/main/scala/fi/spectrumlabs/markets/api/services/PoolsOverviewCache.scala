package fi.spectrumlabs.markets.api.services

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Sync, Timer}
import fi.spectrumlabs.markets.api.models.PoolOverview
import tofu.higherKind.RepresentableK
import tofu.syntax.monadic._

import scala.concurrent.duration.FiniteDuration

trait PoolsOverviewCache[F[_]] {
  def run: F[Unit]
}

object PoolsOverviewCache {

  implicit def representableK: RepresentableK[PoolsOverviewCache] =
    tofu.higherKind.derived.genRepresentableK

  def make[F[_]: Sync: Timer](
    analyticsService: AnalyticsService[F],
    sleepTime: FiniteDuration,
    cache: Ref[F, List[PoolOverview]]
  ): PoolsOverviewCache[F] = new Live[F](analyticsService, cache, sleepTime)

  final private class Live[F[_]: Monad: Timer](
    analyticsService: AnalyticsService[F],
    cache: Ref[F, List[PoolOverview]],
    sleepTime: FiniteDuration
  ) extends PoolsOverviewCache[F] {
    def run: F[Unit] =
      analyticsService.updatePoolsOverview.flatMap { pool =>
        cache.set(pool)
      } >> Timer[F].sleep(sleepTime) >> run
  }
}
