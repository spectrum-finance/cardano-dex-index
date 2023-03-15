package fi.spectrumlabs.db.writer.models.cardano

final case class FullTxOutRef(txOutRefId: TxOutRefId, txOutRefIdx: Int)

final case class TxOutRefId(getTxId: String)


