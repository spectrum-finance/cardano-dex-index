package fi.spectrumlabs.db.writer.classes

import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef

sealed trait ExecutedOrderInfo

object ExecutedOrderInfo {

  final case class ExecutedSwapOrderInfo(
    actualQuote: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    fee: Long,
    orderInputRef: FullTxOutRef
  ) extends ExecutedOrderInfo

  final case class ExecutedDepositOrderInfo(
    amountLq: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    actualX: Long,
    orderInputRef: FullTxOutRef
  ) extends ExecutedOrderInfo

  final case class ExecutedRedeemOrderInfo(
    amountX: Long,
    amountY: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    orderInputRef: FullTxOutRef
  ) extends ExecutedOrderInfo
}
