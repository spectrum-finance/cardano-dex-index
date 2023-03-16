package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class FullTxOutDatum(contents: Option[Contents], tag: String)

@derive(encoder, decoder)
final case class Contents(getDatum: String)