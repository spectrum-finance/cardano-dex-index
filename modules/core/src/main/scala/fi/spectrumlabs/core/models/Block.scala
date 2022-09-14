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

object Block {

  def fromExplorer(block: BlockInfo): Block =
    Block(
      block.id,
      block.height,
      block.timestamp
    )
}
