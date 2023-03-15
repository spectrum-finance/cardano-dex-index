package fi.spectrumlabs.db.writer.models.cardano

//todo: manual encoder/decoder
final case class Confirmed[A](txOut: FullTxOut, element: A)
