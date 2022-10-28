package fi.spectrumlabs.markets.api.models

import fi.spectrumlabs.core.models.domain.{AssetAmount, PoolId}

case class TestPool(
  id: PoolId,
  x: AssetAmount,
  y: AssetAmount,
  timestamp: Long,
  liquidity: Long,
  poolFeeNum: Long,
  poolFeeDen: Long,
  outCollateral: Long,
  lq: String       = "none",
  outputId: String = ""
)
