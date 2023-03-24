package fi.spectrumlabs.db.writer.models

import fi.spectrumlabs.explorer.models.{BlockHash, TxHash}
import fi.spectrumlabs.db.writer.classes.ToSchema
import io.circe.Json
import fi.spectrumlabs.core.models.Tx
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent}
import io.circe.syntax._
import cats.syntax.option._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.{loggable, show}

@derive(decoder, encoder)
final case class Transaction(
  blockHash: BlockHash,
  blockIndex: Long,
  hash: TxHash,
  invalidBefore: Option[BigInt],
  invalidHereafter: Option[BigInt],
  metadata: Option[Json],
  size: Int,
  timestamp: Long
)

object Transaction {

  implicit val toSchemaNew: ToSchema[TxEvent, Transaction] = { case (in: AppliedTransaction) =>
    Transaction(
      BlockHash(in.blockId),
      in.slotNo,
      TxHash(in.txId.getTxId),
      none,
      none,
      none,
      0, //todo: fixme
      in.slotNo //todo:fixme
    )
  }

  implicit val toSchema: ToSchema[Tx, Transaction] = (in: Tx) =>
    Transaction(
      in.blockHash,
      in.blockIndex,
      in.hash,
      in.invalidBefore,
      in.invalidHereafter,
      in.metadata.map(_.asJson),
      in.size,
      in.timestamp
    )
}
