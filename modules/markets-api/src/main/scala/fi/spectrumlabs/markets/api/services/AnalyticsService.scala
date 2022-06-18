package fi.spectrumlabs.markets.api.services

import cats.Monad
import fi.spectrumlabs.markets.api.repositories.repos.PoolsRepository
import tofu.syntax.monadic._

trait AnalyticsService[F[_]] {
  def getPoolTvl(poolId: String): F[BigDecimal]
}

object AnalyticsService {

  final private class Impl[F[_]: Monad](poolRepo: PoolsRepository[F]) extends AnalyticsService[F] {
    def getPoolTvl(poolId: String): F[BigDecimal] =
      for {
        pool <- poolRepo.getPoolById(poolId)

      } yield ()
  }
}