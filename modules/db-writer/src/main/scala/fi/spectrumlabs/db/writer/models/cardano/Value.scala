package fi.spectrumlabs.db.writer.models.cardano

//todo: manual encoder/decoder?
final case class Value(currencySymbol: String, tokenName: String, value: Long)
