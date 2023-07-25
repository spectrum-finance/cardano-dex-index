package fi.spectrumlabs.rates.resolver.mocks

import cats.Applicative
import fi.spectrumlabs.core.AdaAssetClass
import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, AssetClass, Pool, PoolId}
import fi.spectrumlabs.rates.resolver.repositories.Pools

object PoolsMock {
  def make[D[_]: Applicative]: Pools[D] = new Pools[D] {
    def snapshots(minLiquidityValue: Long): D[List[Pool]] = Applicative[D].pure {
      List(
        Pool(
          PoolId("test.1"),
          AssetAmount(AdaAssetClass, Amount(983459834)),
          AssetAmount(AssetClass("t", "2"), Amount(12838))
        ),
        Pool(
          PoolId("test.2"),
          AssetAmount(AdaAssetClass, Amount(36366363)),
          AssetAmount(AssetClass("t2", "2"), Amount(25525))
        ),
        Pool(
          PoolId("test.3"),
          AssetAmount(AdaAssetClass, Amount(2374376447346578L)),
          AssetAmount(AssetClass("t4", "2"), Amount(882723))
        )
      )
    }
  }
}
