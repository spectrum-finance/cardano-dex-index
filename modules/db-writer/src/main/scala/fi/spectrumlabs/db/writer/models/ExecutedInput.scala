package fi.spectrumlabs.db.writer.models

import cats.data.NonEmptyList
import cats.syntax.option.none
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.streaming.AppliedTransaction
import fi.spectrumlabs.explorer.models.{OutRef, TxHash}

final case class ExecutedInput(txHash: TxHash, slot: Long, outRef: OutRef, outIndex: Int)

object ExecutedInput {

  implicit val toSchemaNew: ToSchema[AppliedTransaction, NonEmptyList[ExecutedInput]] = (in: AppliedTransaction) =>
    in.txInputs.map { input =>
      ExecutedInput(
        TxHash(in.txId.getTxId),
        in.slotNo,
        OutRef(input.txInRef.txOutRefId.getTxId),
        input.txInRef.txOutRefIdx.toInt
      )
    }
}
