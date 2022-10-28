package fi.spectrumlabs.markets.api.models

import fi.spectrumlabs.core.models.domain.Coin
import fi.spectrumlabs.core.models.domain.AssetClass.syntax._

object Models {

  import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, AssetClass, PoolId}
  import org.scalacheck._

  object Models {

    def genPoolId: Gen[PoolId] = Gen.listOfN(16, Gen.alphaChar).map(_.mkString).map(PoolId(_))

    def genAssetClass: Gen[AssetClass] =
      for {
        tokenName      <- Gen.listOfN(16, Gen.alphaChar).map(_.mkString)
        currencySymbol <- Gen.listOfN(32, Gen.alphaChar).map(_.mkString)
      } yield AssetClass(currencySymbol, tokenName)

    def getAssetAmount: Gen[AssetAmount] =
      for {
        asset  <- genAssetClass
        amount <- Gen.choose(1000000, 1000000000000L)
      } yield AssetAmount(asset, Amount(amount))

    def genTestPool: Gen[TestPool] =
      for {
        assetX     <- getAssetAmount
        assetY     <- getAssetAmount
        poolId     <- genPoolId
        ts         <- Gen.choose(1900000000L, 2000000000L)
        liquidity  <- Gen.posNum[Long]
        poolFeeNum <- Gen.posNum[Long]
        poolFeeDen <- Gen.posNum[Long]
        outColl    <- Gen.posNum[Long]
      } yield TestPool(poolId, assetX, assetY, ts, liquidity, poolFeeNum, poolFeeDen, outColl)

    def genTxOutRef =
      for {
        txIdx   <- Gen.posNum[Int]
        getTxId <- Gen.alphaStr
      } yield TxOutRef(txIdx, TxOutRefId(getTxId))

    def genTestExecutedSwap: Gen[TestExecutedSwap] =
      for {
        base             <- genAssetClass.map(_.toCoin)
        quote            <- genAssetClass.map(_.toCoin)
        poolId           <- Gen.listOfN(16, Gen.alphaChar).map(_.mkString).map(Coin(_))
        exFeePerTokenNum <- Gen.choose(1000000, 1000000000000L)
        exFeePerTokenDen <- Gen.choose(1000000, 1000000000000L)
        rewardPkh        <- Gen.listOfN(16, Gen.alphaChar).map(_.mkString)
        baseAmount       <- Gen.choose(1000000, 1000000000000L).map(Amount(_))
        actualQuote      <- Gen.choose(1000000, 1000000000000L).map(Amount(_))
        minQuoteAmount   <- Gen.choose(1000000, 1000000000000L).map(Amount(_))
        orderInputId     <- genTxOutRef
        userOutputId     <- genTxOutRef
        poolInputId      <- genTxOutRef
        poolOutputId     <- genTxOutRef
        timestamp        <- Gen.choose(1000000000L, 2000000000L)
      } yield TestExecutedSwap(
        base,
        quote,
        poolId,
        exFeePerTokenNum,
        exFeePerTokenDen,
        rewardPkh,
        None,
        baseAmount,
        actualQuote,
        minQuoteAmount,
        orderInputId,
        userOutputId,
        poolInputId,
        poolOutputId,
        timestamp
      )
  }
}
