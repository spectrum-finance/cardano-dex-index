package fi.spectrumlabs.db.writer.models.cardano

final case class PoolEvent(
  outCollateral: Int,
  poolCoinLq: Coin,
  poolCoinX: Coin,
  poolCoinY: Coin,
  poolFee: PoolFee,
  poolId: Coin,
  poolLiquidity: Long,
  poolReservesX: Long,
  poolReservesY: Long
)
