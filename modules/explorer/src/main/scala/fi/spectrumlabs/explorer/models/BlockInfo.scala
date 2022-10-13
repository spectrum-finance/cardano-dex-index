package fi.spectrumlabs.explorer.models

import derevo.circe.magnolia.decoder
import derevo.derive

@derive(decoder)
final case class BlockInfo(
  id: String,
  height: Int,
  timestamp: Long,
  transactionsCount: Int,
  size: Int,
  difficulty: Long,
  minerReward: Long
)
