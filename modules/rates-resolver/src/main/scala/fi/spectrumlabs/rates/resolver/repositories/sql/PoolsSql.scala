package fi.spectrumlabs.rates.resolver.repositories.sql

import doobie.LogHandler
import doobie.implicits._
import fi.spectrumlabs.core.models.db.{Pool, PoolResolver}

final class PoolsSql(implicit logHandler: LogHandler) {

  //todo: profile request (nested select in join)
  def getAllLatest(minLiquidityValue: Long): doobie.Query0[PoolResolver] =
    sql"""
         |SELECT pool_id, x, reserves_x, y, reserves_y FROM pool p
         |LEFT JOIN (
         |  SELECT pool_id AS pid, max(id) AS id FROM pool GROUP BY pool_id
         |) AS plast ON plast.pid = p.pool_id AND plast.id = p.id
         |WHERE plast.id = p.id AND p.reserves_x >= $minLiquidityValue AND p.reserves_y >= $minLiquidityValue;
       """.stripMargin.query[PoolResolver]
}
