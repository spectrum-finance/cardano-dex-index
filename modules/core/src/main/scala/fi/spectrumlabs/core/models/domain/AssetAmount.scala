package fi.spectrumlabs.core.models.domain

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.{loggable, show}

@derive(decoder, encoder, loggable, show)
final case class AssetAmount(asset: AssetClass, amount: Amount)

object AssetAmount {

  implicit def schema: Schema[AssetAmount] = Schema.derived
}
