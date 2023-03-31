package fi.spectrumlabs.core.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class Block(
  id: String,
  height: Int,
  timestamp: Long
)

