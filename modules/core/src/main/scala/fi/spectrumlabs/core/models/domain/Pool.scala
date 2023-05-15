package fi.spectrumlabs.core.models.domain

import cats.syntax.eq._
import derevo.derive
import tofu.logging.derivation.loggable
import fi.spectrumlabs.core.models.db.{Pool => PoolDb}

@derive(loggable)
final case class Pool(id: PoolId, x: AssetAmount, y: AssetAmount) {

  def contains(elem: AssetClass): Boolean =
    elem === x.asset || elem === y.asset

  def contains(e1: AssetClass, e2: AssetClass): Boolean =
    contains(e1) || contains(e2)
}

object Pool {

  def fromDb(poolDb: PoolDb): Pool =
    Pool(
      PoolId(poolDb.poolId),
      AssetAmount(poolDb.x, Amount(poolDb.xReserves)),
      AssetAmount(poolDb.y, Amount(poolDb.yReserves))
    )
}
