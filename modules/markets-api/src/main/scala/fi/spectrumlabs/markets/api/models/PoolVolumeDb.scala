package fi.spectrumlabs.markets.api.models

import derevo.derive
import fi.spectrumlabs.core.models.domain.{AssetClass, PoolId}
import tofu.logging.derivation.loggable

@derive(loggable)
case class PoolVolumeDb(value: BigDecimal, poolId: PoolId, asset: AssetClass)
