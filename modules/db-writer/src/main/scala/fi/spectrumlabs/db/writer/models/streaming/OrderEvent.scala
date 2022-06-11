package fi.spectrumlabs.db.writer.models.streaming

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(decoder, encoder, loggable)
final case class OrderEvent(stringJson: String)
