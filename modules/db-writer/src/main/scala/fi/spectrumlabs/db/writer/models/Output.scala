package fi.spectrumlabs.db.writer.models

import cats.data.NonEmptyList
import fi.spectrumlabs.explorer.models._
import fi.spectrumlabs.core.models.Tx
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent}
import io.circe.Json
import io.circe.syntax._
import cats.syntax.option._
import doobie.Read

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

  implicit val read: Read[Output] = ???

  implicit val toSchemaNew: ToSchema[TxEvent, NonEmptyList[Output]] = { case (in: AppliedTransaction) =>
    in.txOutputs.map { output =>
      Output(
        TxHash(output.fullTxOutRef.txOutRefId.getTxId),
        output.fullTxOutRef.txOutRefIdx,
        OutRef(output.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ output.fullTxOutRef.txOutRefIdx.toString),
        BlockHash(in.blockId),
        in.slotNo,
        Addr(output.fullTxOutAddress.addressCredential.toString),
        Bytea(""), //todo: fill with orig content
        none,
        output.fullTxOutValue.asJson,
        none,
        none,
        none,
        none
      )
    }
  }

  implicit val toSchema: ToSchema[Tx, NonEmptyList[Output]] = (in: Tx) =>
    in.outputs.map { o =>
      Output(
        in.hash,
        in.blockIndex,
        o.ref,
        o.blockHash,
        o.index.toLong,
        o.addr,
        o.rawAddr,
        o.paymentCred,
        o.value.asJson,
        o.dataHash,
        o.data,
        o.dataBin,
        o.spentByTxHash
      )
    }

}
