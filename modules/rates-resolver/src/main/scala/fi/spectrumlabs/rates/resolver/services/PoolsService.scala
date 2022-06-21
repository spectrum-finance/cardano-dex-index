package fi.spectrumlabs.rates.resolver.services

import cats.Functor
import fi.spectrumlabs.core.models.domain._
import fi.spectrumlabs.rates.resolver.repositories.PoolsRepo
import cats.syntax.functor._

trait PoolsService[F[_]] {
  def getAllLatest(minLiquidityValue: Long): F[List[Pool]]
}

object PoolsService {

  def create[F[_]: Functor](poolsRepo: PoolsRepo[F]): PoolsService[F] = new Impl[F](poolsRepo)

  final private class Impl[F[_]: Functor](poolsRepo: PoolsRepo[F]) extends PoolsService[F] {

    def getAllLatest(minLiquidityValue: Long): F[List[Pool]] =
      poolsRepo
        .getAllLatest(minLiquidityValue)
        .map {
          _.map { poolDb =>
            Pool(
              PoolId(poolDb.poolId),
              AssetAmount(
                AssetClass(
                  poolDb.x.split(".").headOption.getOrElse(""),
                  poolDb.x.split(".").lastOption.getOrElse("")
                ),
                Amount(poolDb.xReserves)
              ),
              AssetAmount(
                AssetClass(
                  poolDb.y.split(".").headOption.getOrElse(""),
                  poolDb.y.split(".").lastOption.getOrElse("")
                ),
                Amount(poolDb.yReserves)
              )
            )
          }
        }
  }
}
