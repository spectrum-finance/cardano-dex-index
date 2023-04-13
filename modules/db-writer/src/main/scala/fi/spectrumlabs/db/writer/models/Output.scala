package fi.spectrumlabs.db.writer.models

import cats.data.NonEmptyList
import fi.spectrumlabs.core.models.{Addr, BlockHash, Bytea, Hash32, OutRef, PaymentCred, TxHash}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent}
import io.circe.Json
import io.circe.syntax._
import cats.syntax.option._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.{Get, Read}
import tofu.logging.derivation.{loggable, show}

@derive(decoder, encoder)
final case class Output(
  txHash: TxHash,
  txIndex: Long,
  ref: OutRef,
  blockHash: BlockHash,
  index: Long,
  addr: Addr,
  rawAddr: Bytea,
  paymentCred: Option[PaymentCred],
  value: Json,
  dataHash: Option[Hash32],
  data: Option[Json],
  dataBin: Option[Bytea],
  spentByTxHash: Option[TxHash]
)

object Output {

  implicit val getJson: Get[Json] = Get[String].map(_ => Json.Null)

  implicit val toSchemaNew: ToSchema[TxEvent, NonEmptyList[Output]] = { case (in: AppliedTransaction) =>
    in.txOutputs.map { output =>
      Output(
        TxHash(output.fullTxOutRef.txOutRefId.getTxId),
        output.fullTxOutRef.txOutRefIdx,
        OutRef(output.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ output.fullTxOutRef.txOutRefIdx.toString),
        BlockHash(in.blockId),
        in.slotNo,
        Addr(output.fullTxOutAddress.addressCredential.toString),
        Bytea(""), //todo: fill with orig content. Will be added in next iteration
        none,
        output.fullTxOutValue.asJson,
        none,
        none,
        none,
        none
      )
    }
  }
}
