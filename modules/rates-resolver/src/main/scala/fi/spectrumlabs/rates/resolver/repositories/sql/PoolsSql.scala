package fi.spectrumlabs.rates.resolver.repositories.sql

import doobie.LogHandler
import doobie.implicits._
import fi.spectrumlabs.core.models.db.DBPoolSnapshot

final class PoolsSql(implicit logHandler: LogHandler) {

  def snapshots(minLiquidityValue: Long): doobie.Query0[DBPoolSnapshot] =
    sql"""
         |SELECT pool_id, x, reserves_x, y, reserves_y
         |FROM pool p
         |INNER JOIN (
         |	SELECT pool_id AS pid, max(id) as id FROM pool GROUP BY pool_id
         |) p1 ON p.pool_id = p1.pid and p.id = p1.id 
         |where (reserves_x >= $minLiquidityValue and x = '.') or (reserves_y >= $minLiquidityValue and y = '.')
       """.stripMargin.query[DBPoolSnapshot]
}
