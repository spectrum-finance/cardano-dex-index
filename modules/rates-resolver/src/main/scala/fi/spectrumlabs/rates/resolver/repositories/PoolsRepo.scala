package fi.spectrumlabs.rates.resolver.repositories

import fi.spectrumlabs.core.models.db.Pool

trait PoolsRepo[D[_]] {
  def getAllLatest(minLiquidityValue: Long): D[List[Pool]]
}

object PoolsRepo {

}