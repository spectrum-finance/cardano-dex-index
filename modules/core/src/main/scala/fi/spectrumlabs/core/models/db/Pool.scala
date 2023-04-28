package fi.spectrumlabs.core.models.db

import derevo.derive
import fi.spectrumlabs.core.models.domain.{AssetClass, PoolFee}
import tofu.logging.derivation.loggable

@derive(loggable)
final case class Pool(poolId: String, x: AssetClass, xReserves: Long, y: AssetClass, yReserves: Long, fees: PoolFee)
