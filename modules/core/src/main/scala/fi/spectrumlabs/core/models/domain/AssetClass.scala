package fi.spectrumlabs.core.models.domain

import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.show._
import cats.{Eq, Show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.util.{Get, Put}
import io.lettuce.core.codec.Base16
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

import java.util.Base64

@derive(decoder, encoder, loggable)
final case class AssetClass(currencySymbol: String, tokenName: String)

object AssetClass {

  object syntax {

    implicit class AssetClassOps(val in: AssetClass) extends AnyVal {
      def toCoin: Coin = Coin(s"${in.currencySymbol}.${in.tokenName}")
    }
  }

  implicit val eq: Eq[AssetClass] = (x: AssetClass, y: AssetClass) =>
    x.tokenName == y.tokenName && x.currencySymbol == y.currencySymbol

  implicit val show: Show[AssetClass] = asset => s"${asset.currencySymbol}.${asset.tokenName}"

  implicit val put: Put[AssetClass] = implicitly[Put[String]].contramap(_.show)

  implicit val get: Get[AssetClass] = implicitly[Get[String]]
    .temap(s => Either.fromOption(AssetClass.fromString(s), s"Incorrect asset class value: $s"))

  implicit val schema: Schema[AssetClass] = Schema.derived

  def fromString(in: String): Option[AssetClass] =
    in match {
      case "." => AssetClass("", "").some //todo fix insertion
      case str =>
        str.split('.').toList match {
          case cs :: tn :: Nil => AssetClass(cs, tn).some
          case _               => none
        }
    }

  def toMetadata(assetClass: AssetClass): String =
    s"${assetClass.currencySymbol}${new String(Base16.encode(assetClass.tokenName.getBytes(), false))}"

}
