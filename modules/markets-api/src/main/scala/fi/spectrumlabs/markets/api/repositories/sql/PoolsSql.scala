package fi.spectrumlabs.markets.api.repositories.sql

import doobie.LogHandler
import doobie.implicits._
import fi.spectrumlabs.markets.api.models.db.Pool

final case class PoolsSql(implicit lh: LogHandler) {

  def getPoolsByAssetId: doobie.Query0[Pool] =
    sql"""
         |SELECT pool_id,x,reserves_x,y,reserves_y FROM pool p
         |	LEFT JOIN (
         |    SELECT pool_id AS pid,max(id)AS id FROM pool GROUP BY pool_id
         |  ) AS plast ON plast.pid=p.pool_id AND plast.id=p.id
         |	WHERE plast.id=p.id;
       """.stripMargin.query[Pool]
}
