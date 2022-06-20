package fi.spectrumlabs.core.models.domain

import cats.syntax.eq._
import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
final case class Pool(id: PoolId, x: AssetAmount, y: AssetAmount) {
  def priceByX: BigDecimal = BigDecimal(y.amount.value) / x.amount.value
  def priceByY: BigDecimal = BigDecimal(x.amount.value) / y.amount.value

  def contains(elem: AssetClass): Boolean =
    elem === x.asset || elem === y.asset
}

object Pool