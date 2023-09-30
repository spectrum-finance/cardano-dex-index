package fi.spectrumlabs.markets.api.services

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Sync, Timer}
import fi.spectrumlabs.markets.api.models.{PoolOverview, PoolOverviewNew}
import tofu.higherKind.RepresentableK
import tofu.syntax.monadic._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait PoolsOverviewCache[F[_]] {
  def run: F[Unit]

  def run2: F[Unit]
}

object PoolsOverviewCache {

  implicit def representableK: RepresentableK[PoolsOverviewCache] =
    tofu.higherKind.derived.genRepresentableK

  def make[F[_]: Sync: Timer](
    analyticsService: AnalyticsService[F],
    sleepTime: FiniteDuration,
    cache: Ref[F, List[PoolOverview]],
      cache2: Ref[F, List[PoolOverviewNew]]
  ): PoolsOverviewCache[F] = new Live[F](analyticsService, cache, cache2, sleepTime)

  final private class Live[F[_]: Monad: Timer](
    analyticsService: AnalyticsService[F],
    cache: Ref[F, List[PoolOverview]],
    cache2: Ref[F, List[PoolOverviewNew]],
    sleepTime: FiniteDuration
  ) extends PoolsOverviewCache[F] {
    def run: F[Unit] =
      analyticsService.updatePoolsOverview.flatMap { pool =>
        cache.set(pool)
      } >> Timer[F].sleep(sleepTime) >> run

    def run2: F[Unit] =
      analyticsService.updateLatestPoolsStates.flatMap { pool =>
        cache2.set(pool)
      } >> Timer[F].sleep(1.seconds) >> run2
  }
}
