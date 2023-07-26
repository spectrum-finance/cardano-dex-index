package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import cats.syntax.either._

@derive(decoder)
final case class OrderAction[A <: Action](action: A, kind: String, poolId: CoinWrapper)

@derive(decoder)
sealed trait Action

@derive(decoder)
final case class SwapAction(
  swapBase: CoinWrapper,
  swapBaseIn: Long,
  swapExFee: SwapExFee,
  swapMinQuoteOut: Long,
  swapPoolId: CoinWrapper,
  swapQuote: CoinWrapper,
  swapRewardPkh: PubKeyHash,
  swapRewardSPkh: Option[StakePubKeyHash]
) extends Action

@derive(decoder)
final case class SwapExFee(exFeePerTokenDen: Long, exFeePerTokenNum: Long)

@derive(decoder)
final case class DepositAction(
  adaCollateral: Long,
  depositExFee: ExFee,
  depositPair: DepositPair,
  depositLq: Coin,
  depositPoolId: CoinWrapper,
  depositRewardPkh: PubKeyHash,
  depositRewardSPkh: Option[StakePubKeyHash]
) extends Action

final case class DepositPair(firstElem: DepositPairElem, secondElem: DepositPairElem)

object DepositPair {

  implicit val decoder: Decoder[DepositPair] = new Decoder[DepositPair] {

    override def apply(c: HCursor): Result[DepositPair] =
      c.values.toRight(DecodingFailure("Deposit pair should contains json array", List.empty)).flatMap { pairs =>
        for {
          first <- pairs.head.as[DepositPairElem]
          second <-
            if (pairs.size == 2) pairs.last.as[DepositPairElem]
            else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
        } yield
          if (first.coin.unAssetClass == AssetClass.ada) DepositPair(first, second) //todo drop when tracker will be fixed
          else DepositPair(second, first)
      }
  }
}

final case class DepositPairElem(coin: Coin, value: Long)

object DepositPairElem {

  implicit val decoder: Decoder[DepositPairElem] = new Decoder[DepositPairElem] {

    override def apply(c: HCursor): Result[DepositPairElem] =
      c.values.toRight(DecodingFailure("Deposit pair element should contains json array", List.empty)).flatMap {
        pairElems =>
          for {
            coin <- pairElems.head.as[Coin]
            value <-
              if (pairElems.size == 2) pairElems.last.as[Long]
              else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
          } yield DepositPairElem(coin, value)
      }
  }
}

@derive(encoder, decoder)
final case class ExFee(unExFee: Long)

@derive(decoder)
final case class RedeemAction(
  redeemExFee: ExFee,
  redeemLq: CoinWrapper,
  redeemLqIn: Long,
  redeemPoolId: CoinWrapper,
  redeemPoolX: CoinWrapper,
  redeemPoolY: CoinWrapper,
  redeemRewardPkh: PubKeyHash,
  redeemRewardSPkh: Option[StakePubKeyHash]
) extends Action
