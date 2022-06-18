package fi.spectrumlabs.markets.api.repositories.repos

import doobie.implicits._
import doobie.util.query.Query0
import doobie.{Fragment, LogHandler}
import fi.spectrumlabs.markets.api.models.db.Pool

trait PoolsRepository[D[_]] {
  def getPoolsByAssetId(assetId: String): D[List[Pool]]

  def getPoolById(poolId: String): D[Pool]
}
