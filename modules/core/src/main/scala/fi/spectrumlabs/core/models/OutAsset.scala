package fi.spectrumlabs.core.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.scalaland.chimney.dsl._

@derive(encoder, decoder)
final case class OutAsset(
  policyId: PolicyId,
  name: Asset32,
  quantity: BigInt,
  jsQuantity: String
)
