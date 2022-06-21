package fi.spectrumlabs.core.models.domain

import cats.{Eq, Show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable, encoder, decoder)
final case class AssetClass(currencySymbol: String, tokenName: String)

object AssetClass {

  implicit val eq: Eq[AssetClass] = (x: AssetClass, y: AssetClass) =>
    x.tokenName == y.tokenName && x.currencySymbol == y.currencySymbol

  implicit val show: Show[AssetClass] = asset =>
    s"${asset.currencySymbol}.${asset.tokenName}"
}
