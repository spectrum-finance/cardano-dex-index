package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class FullTxOutRef(txOutRefId: TxOutRefId, txOutRefIdx: Int)

@derive(encoder, decoder)
final case class TxOutRefId(getTxId: String)


