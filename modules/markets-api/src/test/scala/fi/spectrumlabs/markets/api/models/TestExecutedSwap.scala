package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.util.Put
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import tofu.logging.derivation.{loggable, show}

final case class TestExecutedSwap(
  base: Coin,
  quote: Coin,
  poolId: Coin,
  exFeePerTokenNum: Long,
  exFeePerTokenDen: Long,
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  baseAmount: Amount,
  actualQuote: Amount,
  minQuoteAmount: Amount,
  orderInputId: TxOutRef,
  userOutputId: TxOutRef,
  poolInputId: TxOutRef,
  poolOutputId: TxOutRef,
  timestamp: Long
)

@derive(decoder, encoder, loggable, show)
final case class TxOutRef(txOutRefIdx: Int, txOutRefId: TxOutRefId)

object TxOutRef {
  implicit val put: Put[TxOutRef] = Put[String].contramap(r => s"${r.txOutRefId.getTxId}#${r.txOutRefIdx}")
}

@derive(decoder, encoder, loggable, show)
final case class TxOutRefId(getTxId: String)

@derive(decoder, encoder, loggable, show)
final case class StakePKH(unStakePubKeyHash: StakePubKeyHash)

@derive(decoder, encoder, loggable, show)
final case class StakePubKeyHash(getPubKeyHash: String)

