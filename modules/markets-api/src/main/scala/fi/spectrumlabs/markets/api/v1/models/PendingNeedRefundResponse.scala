package fi.spectrumlabs.markets.api.v1.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema

@derive(encoder, decoder)
final case class PendingNeedRefundResponse(needRefund: Long, pending: Long)

object PendingNeedRefundResponse {

  implicit val schema: Schema[PendingNeedRefundResponse] = Schema.derived
}
