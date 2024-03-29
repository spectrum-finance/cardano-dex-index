package fi.spectrumlabs.markets.api.repositories.sql

import cats.syntax.show._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.log.LogHandler
import doobie.util.query.Query0
import fi.spectrumlabs.core.models.db.Pool
import fi.spectrumlabs.core.models.domain
import fi.spectrumlabs.core.models.domain.{PoolFee, PoolId, Pool => DomainPool}
import fi.spectrumlabs.markets.api.models.db.{AvgAssetAmounts, PoolDb, PoolDbNew, PoolFeeSnapshot}
import fi.spectrumlabs.markets.api.models.{PoolVolume, PoolVolumeDbNew}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow

final class PoolsSql(implicit lh: LogHandler) {

  def getPoolList: Query0[PoolId] =
    sql"""SELECT DISTINCT pool_id FROM pool""".stripMargin.query[PoolId]

  def getPoolStateByDate(poolId: PoolId, date: Long): Query0[Pool] =
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
         |	LEFT JOIN (
         |		SELECT
         |			pool_id AS pid,
         |			max(id) AS id
         |		FROM
         |			pool
         |		WHERE
         |			timestamp <= $date
         |		GROUP BY
         |			pool_id) AS plast ON plast.pid = p.pool_id
         |	AND plast.id = p.id
         |WHERE
         |	plast.id = p.id
         |	AND pool_id = $poolId
         |  AND p.timestamp <= $date;
       """.stripMargin.query[Pool]

  def getPools: Query0[PoolDbNew] =
    sql"""
         |SELECT
         |	pool_id,
         |	x,
         |	reserves_x,
         |	y,
         |	reserves_y,
         |	pool_fee_num,
         |	pool_fee_den,
         |  lq,
         |  liquidity
         |FROM
         |	pool p
         |	INNER JOIN (
         |		SELECT pool_id AS pid, max(id) AS id
         |		FROM pool
         |		GROUP BY pool_id
         |	) pLatest ON p.pool_id = pLatest.pid AND p.id = pLatest.id
       """.stripMargin.query[PoolDbNew]

  def getPoolsOld: Query0[PoolDb] =
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
         |		SELECT pool_id AS pid, max(id) AS id
         |		FROM pool
         |		GROUP BY pool_id
         |	) pLatest ON p.pool_id = pLatest.pid AND p.id = pLatest.id
       """.stripMargin.query[PoolDb]

  def getPool(poolId: PoolId, minLiquidityValue: Long): Query0[Pool] =
    sql"""
         |SELECT
         |	pool_id,
         |	x,
         |	reserves_x,
         |	y,
         |	reserves_y,
         |  pool_fee_num,
         |  pool_fee_den
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

  def getPoolVolume(pool: DomainPool, from: Long): Query0[PoolVolume] =
    sql"""
         |select
         |	p.pool_id,
         |	cast(sum(CASE WHEN (s.base = p.y) THEN s.actual_quote ELSE 0 END) AS BIGINT) AS tx,
         |	cast(sum(CASE WHEN (s.base = p.x) THEN s.actual_quote ELSE 0 END) AS BIGINT) AS ty
         |from swap s left join pool p on (p.output_id=s.pool_input_id)
         |where p.pool_id is not null and p.pool_id=${pool.id} and s.actual_quote is not null and creation_timestamp > $from
         |group by p.pool_id;
       """.stripMargin.query[PoolVolume]

  def getPoolVolumes(tw: TimeWindow): Query0[PoolVolumeDbNew] =
    sql"""
         |select
         |	p.pool_id,
         |	cast(sum(CASE WHEN (s.base = p.y) THEN s.actual_quote ELSE 0 END) AS BIGINT) AS tx,
         |	cast(sum(CASE WHEN (s.base = p.x) THEN s.actual_quote ELSE 0 END) AS BIGINT) AS ty
         |from swap s left join pool p on (p.output_id=s.pool_input_id)
         |where p.pool_id is not null and s.actual_quote is not null ${timeWindowCond(
      tw,
      "and",
      "s",
      "creation_timestamp"
    )}
         |group by p.pool_id;
       """.stripMargin.query[PoolVolumeDbNew]

  def getAvgPoolSnapshot(id: PoolId, tw: TimeWindow, resolution: Long): Query0[AvgAssetAmounts] =
    sql"""
         |SELECT avg(p.reserves_x), AVG(p.reserves_y), AVG(timestamp), timestamp / $resolution AS res
         |FROM pool p
         |WHERE pool_id = $id
         |${timeWindowCond(tw, "and", "p", "timestamp")}
         |GROUP BY res
         |ORDER BY res
         """.stripMargin.query[AvgAssetAmounts]

  def getFirstPoolSwapTime(id: PoolId): Query0[Long] =
    sql"""
         |SELECT coalesce(min(execution_timestamp), 0)
         |FROM swap
         |WHERE pool_nft = $id AND execution_timestamp IS NOT NULL;
       """.stripMargin.query

  def getPoolFees(pool: domain.Pool, window: TimeWindow, poolFee: PoolFee): Query0[PoolFeeSnapshot] = {
    def from = window.from.map(s => Fragment.const(s"execution_timestamp > $s and ")).getOrElse(Fragment.empty)
    def to   = window.to.map(s => Fragment.const(s"execution_timestamp <= $s")).getOrElse(Fragment.empty)

    sql"""
         |SELECT
         |	cast(COALESCE(sum(CASE WHEN (base = ${pool.y.asset.show}) THEN actual_quote::decimal * (${poolFee.feeDen} - ${poolFee.feeNum}) / ${poolFee.feeDen} ELSE 0 END), 0) AS bigint) AS tx,
         |	cast(COALESCE(sum(CASE WHEN (base = ${pool.x.asset.show}) THEN actual_quote::decimal * (${poolFee.feeDen} - ${poolFee.feeNum}) / ${poolFee.feeDen} ELSE 0 END), 0) AS bigint) AS ty
         |FROM swap
         |WHERE pool_nft = ${pool.id} and $from $to
       """.stripMargin.query[PoolFeeSnapshot]
  }

  private def timeWindowCond(tw: TimeWindow, condKeyword: String, alias: String, tsKey: String): Fragment =
    if (tw.from.nonEmpty || tw.to.nonEmpty)
      Fragment.const(
        s"$condKeyword ${tw.from.map(ts => s"$alias.$tsKey >= $ts").getOrElse("")} ${if (tw.from.isDefined && tw.to.isDefined) "and"
        else ""} ${tw.to.map(ts => s"$alias.$tsKey <= $ts").getOrElse("")}"
      )
    else Fragment.empty
}
