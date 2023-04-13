package fi.spectrumlabs.db.writer.models

import cats.data.NonEmptyList
import fi.spectrumlabs.core.models.{OutRef, TxHash}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent}
import cats.syntax.option._

final case class Input(txHash: TxHash, txIndex: Long, outFef: OutRef, outIndex: Int, redeemerIndex: Option[Int])

object Input {

  implicit val toSchemaNew: ToSchema[TxEvent, NonEmptyList[Input]] = { case (in: AppliedTransaction) =>
    in.txInputs.map { input =>
      Input(
        TxHash(in.txId.getTxId),
        in.slotNo,
        OutRef(input.txInRef.txOutRefId.getTxId),
        input.txInRef.txOutRefIdx,
        none[Int] //todo: change to corresponding redeemer id. In current version redeemer support doesn't exist
      )
    }
  }
}
