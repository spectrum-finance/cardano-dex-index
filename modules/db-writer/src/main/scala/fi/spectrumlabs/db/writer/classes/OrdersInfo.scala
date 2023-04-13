package fi.spectrumlabs.db.writer.classes

import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef

object OrdersInfo {

  final case class ExecutedSwapOrderInfo(
    actualQuote: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    orderInputRef: FullTxOutRef
  )

  final case class ExecutedDepositOrderInfo(
    amountLq: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    orderInputRef: FullTxOutRef
  )

  final case class ExecutedRedeemOrderInfo(
    amountX: Long,
    amountY: Long,
    userOutputRef: FullTxOutRef,
    poolInputRef: FullTxOutRef,
    poolOutputRef: FullTxOutRef,
    executionTimestamp: Long,
    orderInputRef: FullTxOutRef
  )
}
