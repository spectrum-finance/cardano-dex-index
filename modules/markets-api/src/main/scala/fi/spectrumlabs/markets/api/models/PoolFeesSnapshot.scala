package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{AssetAmount, PoolId}

@derive(encoder, decoder)
final case class PoolFeesSnapshot(
  poolId: PoolId,
  feesByX: AssetAmount,
  feesByY: AssetAmount
)
