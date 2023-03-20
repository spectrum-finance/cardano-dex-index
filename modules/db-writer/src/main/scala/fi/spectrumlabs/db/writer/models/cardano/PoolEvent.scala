package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class PoolEvent(
  outCollateral: Long,
  poolCoinLq: CoinWrapper,
  poolCoinX: CoinWrapper,
  poolCoinY: CoinWrapper,
  poolFee: PoolFee,
  poolId: CoinWrapper,
  poolLiquidity: Long,
  poolReservesX: Long,
  poolReservesY: Long
)
