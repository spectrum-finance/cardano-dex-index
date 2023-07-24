package fi.spectrumlabs.markets.api.models

import derevo.derive
import fi.spectrumlabs.core.models.domain.PoolId
import tofu.logging.derivation.loggable

@derive(loggable)
case class PoolVolumeDbNew(poolId: PoolId, x: BigDecimal, y: BigDecimal)
