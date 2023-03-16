package fi.spectrumlabs.db.writer.models.cardano

import io.circe.Decoder

//todo: manual encoder/decoder
final case class Confirmed[A](txOut: FullTxOut, element: A)

object Confirmed {

}
