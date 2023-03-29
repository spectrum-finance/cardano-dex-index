package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.Coin
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutValue.flattenValues

@derive(encoder, decoder)
final case class FullTxOutValue(getValue: List[Values]) {

  val flattenValue = flattenValues(this)

  def contains(coin: Coin): Boolean =
    flattenValue.contains(coin.value)

  // return token value in next format: (policyId(base16encoding).tokenName -> tokenValue)
  def find(coin: Coin): Option[(String, Long)] =
    flattenValue.find(_._1 == coin.value)
}

object FullTxOutValue {

  def flattenValues(fullTxOutValue: FullTxOutValue): Map[String, Long] =
    fullTxOutValue.getValue.flatMap { values =>
      values.tokens.map(tokenValue =>
        s"${values.curSymbol.unCurrencySymbol}.${tokenValue.tokenName.unTokenName}" -> tokenValue.value
      )
    }.toMap
}
