package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.AssetClass.syntax._
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, PoolEvent}
import fi.spectrumlabs.db.writer.models.orders._
import fi.spectrumlabs.db.writer.models.streaming

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
)

object Pool {

  implicit val toSchemaNew: ToSchema[Confirmed[PoolEvent], Pool] =
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
        0
      )

//  implicit val toSchema: ToSchema[streaming.PoolEvent, Pool] =
//    (in: streaming.PoolEvent) =>
//      Pool(
//        in.pool.id.toCoin,
//        in.pool.reservesX,
//        in.pool.reservesY,
//        in.pool.liquidity,
//        in.pool.x.toCoin,
//        in.pool.y.toCoin,
//        in.pool.lq.toCoin,
//        in.pool.fee.poolFeeNum,
//        in.pool.fee.poolFeeDen,
//        in.pool.outCollateral,
//        in.outputId,
//        in.timestamp
//      )

}
