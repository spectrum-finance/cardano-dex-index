package fi.spectrumlabs.db.writer.models.streaming

import cats.data.NonEmptyList
import fi.spectrumlabs.db.writer.models.cardano.{FullTxOut, TxId, TxInput}
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import cats.syntax.either._

trait TxEvent

object TxEvent {

  implicit val decoder: Decoder[TxEvent] = new Decoder[TxEvent] {
    override def apply(c: HCursor): Result[TxEvent] =
      c.as[UnAppliedTransaction].orElse(c.as[AppliedTransaction])
  }
}

final case class UnAppliedTransaction(
  txId: String
) extends TxEvent

object UnAppliedTransaction {

  implicit val decoder: Decoder[UnAppliedTransaction] = new Decoder[UnAppliedTransaction] {
    override def apply(c: HCursor): Result[UnAppliedTransaction] =
      c.values.toRight(DecodingFailure("UnAppliedTransaction doesn't contain values", List.empty)).flatMap {
        case values if values.size == 1 => values.head.as[String].map(UnAppliedTransaction.apply)
        case _                          => DecodingFailure("UnAppliedTransaction should contain only one value", List.empty).asLeft
      }
  }
}

// corresponding to MinimalTx with AppliedTx wrapper
final case class AppliedTransaction(
  blockId: String,
  slotNo: Long,
  txId: TxId,
  txInputs: NonEmptyList[TxInput],
  txOutputs: NonEmptyList[FullTxOut]
) extends TxEvent

object AppliedTransaction {

  implicit val decoder: Decoder[AppliedTransaction] = new Decoder[AppliedTransaction] {

    override def apply(c: HCursor): Result[AppliedTransaction] =
      c.values.toRight(DecodingFailure("AppliedTransaction doesn't contain values", List.empty)).flatMap {
        appliedTxValues =>
          appliedTxValues.head.as[String].flatMap {
            case "AppliedTx" =>
              appliedTxValues.last.hcursor.values
                .toRight(DecodingFailure("MinimalTx doesn't contain values", List.empty))
                .flatMap { minimalTxValues =>
                  for {
                    blockId <- minimalTxValues.last.hcursor.downField("blockId").as[String]
                    slotNo  <- minimalTxValues.last.hcursor.downField("slotNo").as[Long]
                    txId    <- minimalTxValues.last.hcursor.downField("txId").as[TxId]
                    inputs <-
                      minimalTxValues.last.hcursor
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
            case _ =>
              DecodingFailure("MinimalLedgerTx should contain 'MinimalLedgerTx' as first elem", List.empty).asLeft
          }
      }
  }
}
