package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.db.writer.classes.FromLedger
import fi.spectrumlabs.db.writer.models.orders._
import fi.spectrumlabs.db.writer.models.streaming
import io.circe.parser.parse
import fi.spectrumlabs.db.writer.models.orders.AssetClass.syntax._

final case class ExecutedDeposit(
  poolId: PoolId,
  coinX: Coin,
  coinY: Coin,
  coinLq: Coin,
  amountX: Amount,
  amountY: Amount,
  amountLq: Amount,
  exFee: ExFee,
  rewardPkh: PublicKeyHash,
  stakePkh: Option[PublicKeyHash],
  collateralAda: CollateralAda,
  orderInputId: BoxId,
  userOutputId: BoxId,
  poolInputId: BoxId,
  poolOutputId: BoxId
)

object ExecutedDeposit {

  implicit val fromLedger: FromLedger[streaming.ExecutedOrderEvent, Option[ExecutedDeposit]] =
    (in: streaming.ExecutedOrderEvent) =>
      parse(in.stringJson).toOption
        .flatMap(_.as[streaming.ExecutedDeposit].toOption)
        .map { deposit =>
          ExecutedDeposit(
            deposit.config.depositPoolId,
            deposit.config.depositPair._1.value._1.asName,
            deposit.config.depositPair._2.value._1.asName,
            deposit.rewardLq.value._1.asName,
            deposit.config.depositPair._1.value._2,
            deposit.config.depositPair._2.value._2,
            deposit.rewardLq.value._2,
            deposit.config.depositExFee,
            deposit.config.depositRewardPkh,
            deposit.config.depositRewardSPkh,
            deposit.config.adaCollateral,
            deposit.orderInputId,
            deposit.userOutputId,
            deposit.poolInputId,
            deposit.poolOutputId
          )
      }
}
