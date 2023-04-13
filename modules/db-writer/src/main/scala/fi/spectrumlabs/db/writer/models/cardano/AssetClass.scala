package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}

@derive(encoder)
final case class AssetClass(unCurrencySymbol: CurrencySymbol, unTokenName: TokenName)

object AssetClass {

  implicit val decoder: Decoder[AssetClass] = new Decoder[AssetClass] {
    override def apply(c: HCursor): Result[AssetClass] =
      c.values.toRight(DecodingFailure("AssetClass doesn't contain values", List.empty)).flatMap { values =>
        for {
          curSymbol <- values.head.as[CurrencySymbol]
          tkName <- values.last.as[TokenName]
        } yield AssetClass(curSymbol, tkName)
      }
  }
}
