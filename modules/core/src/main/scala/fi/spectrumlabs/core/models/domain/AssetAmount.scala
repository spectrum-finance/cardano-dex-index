package fi.spectrumlabs.core.models.domain

import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
final case class AssetAmount(asset: AssetClass, amount: Amount)
