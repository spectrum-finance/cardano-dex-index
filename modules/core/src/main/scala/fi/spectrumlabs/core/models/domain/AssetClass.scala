package fi.spectrumlabs.core.models.domain

import cats.Eq
import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
final case class AssetClass(currencySymbol: String, tokenName: String)

object AssetClass {

  implicit val eq: Eq[AssetClass] = (x: AssetClass, y: AssetClass) =>
    x.tokenName == y.tokenName && x.currencySymbol == y.currencySymbol
}
