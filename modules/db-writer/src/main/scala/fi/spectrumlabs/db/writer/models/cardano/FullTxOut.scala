package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class FullTxOut(
  fullTxOutAddress: FullTxOutAddress,
  fullTxOutDatum: FullTxOutDatum,
  fullTxOutRef: FullTxOutRef,
  fullTxOutScriptRef: Option[String],
  fullTxOutValue: FullTxOutValue
)