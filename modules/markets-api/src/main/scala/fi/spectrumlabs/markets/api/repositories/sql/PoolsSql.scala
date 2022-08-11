package fi.spectrumlabs.markets.api.repositories.sql

import doobie.util.log.LogHandler
import doobie.implicits._
import doobie.util.query.Query0
import fi.spectrumlabs.core.models.db.Pool
import fi.spectrumlabs.core.models.domain.{PoolId, Pool => DomainPool}
import fi.spectrumlabs.markets.api.models.PoolVolume
import fi.spectrumlabs.markets.api.repositories._

import scala.concurrent.duration.FiniteDuration
import cats.syntax.show._
import fi.spectrumlabs.markets.api.models.db.PoolDb

final class PoolsSql(implicit lh: LogHandler) {

  def getPools: Query0[PoolDb] =
    sql"""
        |SELECT
        |	pool_id,
        |	x,
        |	reserves_x,
        |	y,
        |	reserves_y,
        |	pool_fee_num,
        |	pool_fee_den
        |FROM
        |	pool p
        |	INNER JOIN (
        |		SELECT
        |			pool_id AS pid,
        |			max(timestamp) AS ts
        |		FROM
        |			pool
        |		GROUP BY
        |			pool_id) pLatest ON p.pool_id = pLatest.pid
        |	AND p.timestamp = pLatest.ts
       """.stripMargin.query[PoolDb]

  def getPool(poolId: PoolId, minLiquidityValue: Long): Query0[Pool] =
    sql"""
         |SELECT
         |	pool_id,
         |	x,
         |	reserves_x,
         |	y,
         |	reserves_y
         |FROM
         |	pool p
         |	LEFT JOIN (
         |		SELECT
         |			pool_id AS pid,
         |			max(id) AS id
         |		FROM
         |			pool
         |		GROUP BY
         |			pool_id) AS plast ON plast.pid = p.pool_id
         |	AND plast.id = p.id
         |WHERE
         |	plast.id = p.id
         |	AND p.reserves_x >= $minLiquidityValue
         |	AND p.reserves_y >= $minLiquidityValue
         |	AND pool_id = $poolId;
       """.stripMargin.query[Pool]

  def getPoolVolume(pool: DomainPool, period: FiniteDuration): Query0[PoolVolume] =
    sql"""
         |SELECT
         |	*
         |FROM (
         |	SELECT
         |		sum(actual_quote)
         |	FROM
         |		executed_swap
         |	WHERE
         |		pool_nft = ${pool.id}
         |		AND base = ${pool.x.asset.show}
         |		AND timestamp > ${period.toSeconds}) x
         |	CROSS JOIN (
         |		SELECT
         |			sum(actual_quote)
         |		FROM
         |			executed_swap
         |		WHERE
         |			pool_nft = ${pool.id}
         |			AND base = ${pool.y.asset.show}
         |			AND timestamp > ${period.toSeconds}) y
       """.stripMargin.query[PoolVolume]
}
