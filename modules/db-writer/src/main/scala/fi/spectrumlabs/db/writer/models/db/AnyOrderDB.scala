package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, Coin}
import fi.spectrumlabs.db.writer.models.orders.{StakePKH, TxOutRef}

final case class AnyOrderDB(
  orderInputId: TxOutRef,
  orderType: OrderTypeDB,
  poolId: Coin,
  swapBase: Option[AssetAmount],
  swapQuote: Option[AssetAmount],
  actualQuote: Option[Amount],
  depositX: Option[AssetAmount],
  depositY: Option[AssetAmount],
  depositLq: Option[AssetAmount],
  redeemLq: Option[AssetAmount],
  redeemX: Option[AssetAmount],
  redeemY: Option[AssetAmount],
  exFee: Option[Amount],
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus,
  redeemOutputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef]
)
