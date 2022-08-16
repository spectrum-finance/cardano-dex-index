package fi.spectrumlabs.markets.api.models.db

import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
final case class AvgAssetAmounts(
  amountX: Long,
  amountY: Long,
  timestamp: Long
)
