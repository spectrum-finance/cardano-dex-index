package fi.spectrumlabs.rates.resolver.gateways

import fi.spectrumlabs.core.models.domain.{AssetAmount, AssetClass}
import fi.spectrumlabs.core.models.rates.ResolvedRate

trait NetworkClient[F[_]] {
  def getPrice(asset: AssetClass): F[ResolvedRate]
}
