package fi.spectrumlabs.db.writer.models.streaming

import cats.data.NonEmptyList
import fi.spectrumlabs.db.writer.models.cardano.{FullTxOut, TxId, TxInput}
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}

// corresponding to MinimalTx with AppliedTx wrapper
final case class AppliedTransaction(
  blockId: String,
  slotNo: Long,
  txId: TxId,
  txInputs: NonEmptyList[TxInput],
  txOutputs: NonEmptyList[FullTxOut]
)

object AppliedTransaction {

  //todo: only for testing. Derive it
  implicit val decoder: Decoder[AppliedTransaction] = new Decoder[AppliedTransaction] {

    override def apply(c: HCursor): Result[AppliedTransaction] =
      c.values.toRight(DecodingFailure("AppliedTransaction doesn't contain values", List.empty)).flatMap {
        appliedTxValues =>
          println(appliedTxValues.last)
          appliedTxValues.last.hcursor.values
            .toRight(DecodingFailure("MinimalTx doesn't contain values", List.empty))
            .flatMap { minimalTxValues =>
              for {
                blockId <- minimalTxValues.last.hcursor.downField("blockId").as[String]
                slotNo  <- minimalTxValues.last.hcursor.downField("slotNo").as[Long]
                txId    <- minimalTxValues.last.hcursor.downField("txId").as[TxId]
                inputs <- minimalTxValues.last.hcursor
                            .downField("txInputs")
                            .as[List[TxInput]]
                            .flatMap(NonEmptyList.fromList(_).toRight(DecodingFailure("Empty inputs list", List.empty)))
                txOutputs <-
                  minimalTxValues.last.hcursor
                    .downField("txOutputs")
                    .as[List[FullTxOut]]
                    .flatMap(NonEmptyList.fromList(_).toRight(DecodingFailure("Empty outputs list", List.empty)))
              } yield AppliedTransaction(blockId, slotNo, txId, inputs, txOutputs)
            }
      }
  }
}
