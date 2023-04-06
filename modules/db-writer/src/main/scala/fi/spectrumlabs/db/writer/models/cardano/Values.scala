package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax._
import cats.syntax.either._
import cats.syntax.traverse._

@derive(encoder)
final case class Values(curSymbol: CurrencySymbol, tokens: List[TokenValue])

object Values {

  implicit val decoder: Decoder[Values] = new Decoder[Values] {

    override def apply(c: HCursor): Result[Values] = {
      c.values.toRight(DecodingFailure("test", List.empty)).flatMap { valuesArray =>
        for {
          curSymbol <- valuesArray.head.as[CurrencySymbol]
          tokens <- if (valuesArray.size == 2)
                      {
                        valuesArray.last.hcursor.values
                          .toRight(DecodingFailure("test1", List.empty))
                          .flatMap(l => l.toList.traverse(_.as[TokenValue]))
                      }
                    else List.empty[TokenValue].asRight
        } yield Values(curSymbol, tokens)
      }
    }
  }
}

@derive(encoder, decoder)
final case class CurrencySymbol(unCurrencySymbol: String)

@derive(encoder)
final case class TokenValue(tokenName: TokenName, value: Long)

@derive(encoder, decoder)
final case class TokenName(unTokenName: String)

object TokenValue {

  implicit val decoder: Decoder[TokenValue] = new Decoder[TokenValue] {

    override def apply(c: HCursor): Result[TokenValue] = {
      c.values.toRight(DecodingFailure("No array in tokenValue", List.empty)).flatMap { array =>
        for {
          tokenName <- array.head.as[TokenName]
          value <- if (array.size == 2) array.last.as[Long]
                   else Left(DecodingFailure("No value in tokenName", List.empty))
        } yield (TokenValue(tokenName, value))
      }
    }
  }
}
