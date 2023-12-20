package fi.spectrumlabs.markets.api.services

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Sync, Timer}
import tofu.higherKind.RepresentableK
import tofu.syntax.monadic._

import scala.concurrent.duration.FiniteDuration

trait RatesCache[F[_]] {
  def run: F[Unit]
}

object RatesCache {
  implicit def representableK: RepresentableK[RatesCache] =
    tofu.higherKind.derived.genRepresentableK

  def make[F[_]: Sync: Timer](
    network: Network[F],
    sleepTime: FiniteDuration,
    cache: Ref[F, Option[BigDecimal]]
  ): RatesCache[F] = new Live[F](network, cache, sleepTime)

  final private class Live[F[_]: Monad: Timer](
    network: Network[F],
    cache: Ref[F, Option[BigDecimal]],
    sleepTime: FiniteDuration
  ) extends RatesCache[F] {
    def run: F[Unit] =
      network.getAdaPriceCMC.flatMap { price =>
        cache.set(price)
      } >> Timer[F].sleep(sleepTime) >> run
  }
}
