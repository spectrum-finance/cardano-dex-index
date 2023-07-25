package fi.spectrumlabs.markets.api.models

import derevo.derive
import fi.spectrumlabs.core.models.domain.PoolId
import tofu.logging.derivation.loggable

@derive(loggable)
final case class PoolVolume(poolId: PoolId, xVolume: Option[BigDecimal], yVolume: Option[BigDecimal])
