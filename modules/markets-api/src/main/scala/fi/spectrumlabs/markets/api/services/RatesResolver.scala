package fi.spectrumlabs.markets.api.services

import cats.Monad
import fi.spectrumlabs.markets.api.repositories.repos.PoolsRepository
import tofu.syntax.monadic._

trait RatesResolver[F[_]] {
  //todo apply to pool data model instead
  def resolve(tokenA: String, tokenB: String, reservesA: Long, reservesB: Long, overTokenId: String): F[BigDecimal]
}

object RatesResolver {

  final private class Impl[F[_]: Monad](poolsRepository: PoolsRepository[F]) extends RatesResolver[F] {

    def resolve(
      tokenA: String,
      tokenB: String,
      reservesA: Long,
      reservesB: Long,
      overTokenId: String
    ): F[BigDecimal] = {
      Monad[F].ifM(tokenB == overTokenId pure)(
        ifTrue  = BigDecimal(reservesA / reservesB).pure, //todo calc price correct
        ifFalse = calcPrice
      )
      def calcPrice =
        for {
          poolsRelativeTo <- poolsRepository.getPoolsByAssetId(overTokenId)
          poolToOverToken = poolsRelativeTo.find(p => p.x == tokenA && p.y == tokenB) //todo which pool is better to get?

        } yield BigDecimal(1)

      ???
    }
  }
}
