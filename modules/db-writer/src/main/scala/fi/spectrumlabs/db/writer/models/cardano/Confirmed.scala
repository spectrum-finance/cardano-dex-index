package fi.spectrumlabs.db.writer.models.cardano

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.parser._
import cats.syntax.either._

//todo: manual encoder/decoder
final case class Confirmed[A](txOut: FullTxOut, element: A)

object Confirmed {

  implicit def decoder[A: Decoder]: Decoder[Confirmed[A]] = new Decoder[Confirmed[A]] {

    override def apply(c: HCursor): Result[Confirmed[A]] =
      c.values.toRight(DecodingFailure("Confirmed doesn't contain array value", List.empty)).flatMap { values =>
        for {
          txOut <- values.head.as[FullTxOut]
          a <-
            if (values.size == 2) values.last.as[A]
            else DecodingFailure("Confirmed doesn't contain 2 elements", List.empty).asLeft[A]
        } yield Confirmed(txOut, a)
      }
  }
}
