package fi.spectrumlabs.db.writer.classes

object OrdersInfo {

  final case class SwapOrderInfo(
    actualQuote: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  )

  final case class DepositOrderInfo(
    amountLq: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  )

  final case class RedeemOrderInfo(
    amountX: Long,
    amountY: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  )
}
