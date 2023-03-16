package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax._
import cats.syntax.either._
import cats.syntax.traverse._

//todo: manual encoder/decoder?
@derive(encoder)
final case class Values(curSymbol: CurrencySymbol, tokens: List[TokenValue])

object Values {

//  implicit val encoder: Encoder[Values] = new Encoder[Values] {
//    override def apply(a: Values): Json =
//      Json.arr(
//        a.curSymbol.asJson,
//        a.tokens.asJson
//      )
//  }

  implicit val decoder: Decoder[Values] = new Decoder[Values] {

    override def apply(c: HCursor): Result[Values] = {
      println(c.values)
      c.values.toRight(DecodingFailure("test", List.empty)).flatMap { valuesArray =>
        println(s"valuesArray: $valuesArray")
        println(valuesArray.head.as[CurrencySymbol])
        for {
          curSymbol <- valuesArray.head.as[CurrencySymbol]
          tokens <- if (valuesArray.size == 2)
                      {
                        println(s"valuesArray.last.hcursor.downArray.values: ${valuesArray.last.hcursor.values}")
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
      println(s"token value cursor: ${c}")
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
