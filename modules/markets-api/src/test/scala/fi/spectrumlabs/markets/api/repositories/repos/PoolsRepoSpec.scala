package fi.spectrumlabs.markets.api.repositories.repos

import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.log.LogHandler
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Coin, PoolId}
import fi.spectrumlabs.markets.api.models.Models.Models.{genAssetClass, genTestExecutedSwap, genTestPool}
import fi.spectrumlabs.markets.api.models.db.AvgAssetAmounts
import fi.spectrumlabs.markets.api.models.{PoolVolumeDb, TestExecutedSwap, TestPool}
import fi.spectrumlabs.markets.api.repositories.DbTest
import fi.spectrumlabs.markets.api.repositories.sql.PoolsSql
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should._

class PoolsRepoSpec extends AnyFlatSpec with Matchers with DbTest {

  "PoolsRepo" should "insert/getAllByTxId" in {
    withLiveRepos { pools =>
      val basePoolIdOpt =
        genAssetClass.flatMap(base => Gen.listOfN(16, Gen.alphaChar).map(_.mkString).map(pool => (base, pool))).sample
      Gen.listOfN(5, genTestExecutedSwap).sample.foreach { xs =>
        val swaps =
          basePoolIdOpt.toList
            .flatMap { case (qBase, pool) => xs.map(_.copy(base = qBase.toCoin, poolId = Coin(pool))) }
        swaps.foreach(insertTestExecutedSwap(_).transact(xa).unsafeRunSync())
        basePoolIdOpt.foreach { case (base, pool) =>
          val expectation = List(PoolVolumeDb(swaps.map(_.actualQuote.value).sum, PoolId(pool), base))
          pools
            .getPoolVolumes(TimeWindow(None, None))
            .transact(xa)
            .unsafeRunSync() should be(expectation)
        }
      }
    }
  }

  it should "getAveragePoolSnapshot" in {
    withLiveRepos { pools =>
      Gen.listOfN(50, genTestPool).sample.foreach { xs =>
        val poolId     = Gen.listOfN(16, Gen.alphaChar).map(_.mkString).map(PoolId(_)).sample
        val testPools  = poolId.toList.flatMap(pool => xs.map(_.copy(id = pool)))
        val resolution = 100000
        testPools.foreach(insertTestPool(_).transact(xa).unsafeRunSync())
        poolId.foreach(id =>
          pools
            .getAvgPoolSnapshot(id, TimeWindow(None, None), resolution)
            .transact(xa)
            .unsafeRunSync() should contain theSameElementsAs
          testPools
            .groupBy(_.timestamp / (resolution * 60))
            .map { case (ts, testPools) =>
              val size = testPools.size
              AvgAssetAmounts(
                testPools.map(_.x.amount.value).sum / size,
                testPools.map(_.y.amount.value).sum / size,
                ts
              )
            }
            .toList
        )
      }
    }
  }

  def insertTestPool(pool: TestPool): doobie.ConnectionIO[Int] =
    sql"""
         |INSERT INTO pool
         |(pool_id, x, reserves_x, y, reserves_y, timestamp, liquidity,
         | pool_fee_num, pool_fee_den, out_collateral, lq, output_id)
         |VALUES
         |(${pool.id},${pool.x.asset},${pool.x.amount},${pool.y.asset},${pool.y.amount},${pool.timestamp},
         |${pool.liquidity},${pool.poolFeeNum},${pool.poolFeeDen},${pool.outCollateral},${pool.lq},${pool.outputId})
       """.stripMargin.update.run

  def insertTestExecutedSwap(swap: TestExecutedSwap): doobie.ConnectionIO[Int] =
    sql"""INSERT INTO executed_swap
         |(   base,
         |    quote,
         |    pool_nft,
         |    ex_fee_per_token_num,
         |    ex_fee_per_token_den,
         |    reward_pkh,
         |    stake_pkh,
         |    base_amount,
         |    actual_quote,
         |    min_quote_amount,
         |    order_input_id,
         |    user_output_id,
         |    pool_input_Id,
         |    pool_output_Id,
         |    timestamp)
         |VALUES
         |(${swap.base},${swap.quote},${swap.poolId},${swap.exFeePerTokenNum},${swap.exFeePerTokenDen},
         |${swap.rewardPkh},${swap.stakePkh},${swap.baseAmount},${swap.actualQuote},${swap.minQuoteAmount},
         |${swap.orderInputId},${swap.userOutputId},${swap.poolInputId},${swap.poolOutputId},${swap.timestamp})
       """.stripMargin.update.run

  private def withLiveRepos(
    body: PoolsRepo[ConnectionIO] => Any
  ): Any = {
    implicit val lh = LogHandler.jdkLogHandler
    body(
      new PoolsRepo.Impl(new PoolsSql())
    )
  }
}
