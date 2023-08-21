package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.AssetClass.syntax._
import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, PoolEvent}
import fi.spectrumlabs.db.writer.models.orders._
import fi.spectrumlabs.db.writer.models.{TokenInfo, streaming}

final case class Pool(
  id: Coin,
  reservesX: Amount,
  reservesY: Amount,
  liquidity: Amount,
  x: Coin,
  y: Coin,
  lq: Coin,
  poolFeeNum: Long,
  poolFeeDen: Long,
  outCollateral: Amount,
  outputId: TxOutRef,
  timestamp: Long
) {

  def outputAmount(inputC: Coin, inputA: Long): Long = {
    def out(in: Long, out: Long): BigInt =
      BigInt(out) * inputA * poolFeeNum / (BigInt(in) * poolFeeDen + BigInt(inputA) * poolFeeNum)

    if (inputC == x) out(reservesX.value, reservesY.value) else out(reservesY.value, reservesX.value)
  }.toLong

  def isVerified(in: List[TokenInfo]): Boolean =
    in.exists(_.token == x.value) && in.exists(_.token == y.value)
}

object Pool {

  def toSchemaNew(config: CardanoConfig): ToSchema[Confirmed[PoolEvent], Pool] =
    (in: Confirmed[PoolEvent]) =>
      Pool(
        castFromCardano(in.element.poolId.unCoin.unAssetClass).toCoin,
        Amount(in.element.poolReservesX),
        Amount(in.element.poolReservesY),
        Amount(in.element.poolLiquidity),
        castFromCardano(in.element.poolCoinX.unCoin.unAssetClass).toCoin,
        castFromCardano(in.element.poolCoinY.unCoin.unAssetClass).toCoin,
        castFromCardano(in.element.poolCoinLq.unCoin.unAssetClass).toCoin,
        in.element.poolFee.poolFeeNum,
        in.element.poolFee.poolFeeDen,
        Amount(in.element.outCollateral),
        castFromCardano(in.txOut.fullTxOutRef),
        config.startTimeInSeconds + in.slotNo
      )
}
