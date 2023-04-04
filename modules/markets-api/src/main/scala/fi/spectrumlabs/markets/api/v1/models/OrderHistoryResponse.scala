package fi.spectrumlabs.markets.api.v1.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder)
final case class OrderHistoryResponse(orders: List[UserOrderInfo], total: Long)

object OrderHistoryResponse {

  implicit val schema: Schema[OrderHistoryResponse] =
    Schema
      .derived[OrderHistoryResponse]
      .modify(_.orders)(_.description("Order list"))
      .modify(_.total)(_.description("Total orders num"))
}
