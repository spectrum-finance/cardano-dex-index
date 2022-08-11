package fi.spectrumlabs.core.models.domain

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(loggable, encoder, decoder)
final case class PoolFee(feeNum: BigDecimal, feeDen: BigDecimal)

object PoolFee {
  implicit val schema: Schema[PoolFee] = Schema.derived
}
