package fi.spectrumlabs.core.models.db

import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
final case class DBPoolSnapshot(poolId: String, x: String, xReserves: Long, y: String, yReserves: Long)
