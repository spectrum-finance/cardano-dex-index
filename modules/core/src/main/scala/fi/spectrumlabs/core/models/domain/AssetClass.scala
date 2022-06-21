package fi.spectrumlabs.core.models.domain

import cats.{Applicative, Eq, Show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.Throws
import tofu.logging.derivation.loggable
import tofu.syntax.raise._
import tofu.syntax.monadic._
import cats.syntax.option._

@derive(loggable, encoder, decoder)
final case class AssetClass(currencySymbol: String, tokenName: String)

object AssetClass {

  implicit val eq: Eq[AssetClass] = (x: AssetClass, y: AssetClass) =>
    x.tokenName == y.tokenName && x.currencySymbol == y.currencySymbol

  implicit val show: Show[AssetClass] = asset => s"${asset.currencySymbol}.${asset.tokenName}"

  def fromStringEff[F[_]: Throws: Applicative](in: String): F[AssetClass] =
    in.split(".").toList match {
      case cs :: tn :: Nil => AssetClass(cs, tn).pure[F]
      case err             => new Throwable(s"Got incorrect asset id: $err").raise
    }

  def fromString(in: String): Option[AssetClass] =
    in match {
      case "." => AssetClass("", "").some //todo fix insertion
      case str =>
        str.split('.').toList match {
          case cs :: tn :: Nil => AssetClass(cs, tn).some
          case _               => none
        }
    }

}
