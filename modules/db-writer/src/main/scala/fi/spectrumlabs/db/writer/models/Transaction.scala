package fi.spectrumlabs.db.writer.models

import fi.spectrumlabs.db.writer.classes.ToSchema
import io.circe.Json
import fi.spectrumlabs.core.models.{BlockHash, TxHash}
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent}
import io.circe.syntax._
import cats.syntax.option._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

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
      0, //todo: add tx size in next iteration
      in.slotNo //todo: should be timestamp. Change in next itaration
    )
  }
}
