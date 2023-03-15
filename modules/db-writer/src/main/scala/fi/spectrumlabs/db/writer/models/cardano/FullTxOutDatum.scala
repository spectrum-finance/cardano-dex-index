package fi.spectrumlabs.db.writer.models.cardano

final case class FullTxOutDatum (contents: Contents, tag: String)

final case class Contents(getDatum: String)