package fi.spectrumlabs.markets.api.models.db

import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, AssetClass, PoolId}
import tofu.logging.derivation.loggable

@derive(loggable)
final case class PoolDbNew(
  poolId: PoolId,
  x: AssetClass,
  xReserves: Amount,
  y: AssetClass,
  yReserves: Amount,
  feeNum: BigDecimal,
  feeDen: BigDecimal,
  lq: AssetClass,
  lqReserved: Amount
)
