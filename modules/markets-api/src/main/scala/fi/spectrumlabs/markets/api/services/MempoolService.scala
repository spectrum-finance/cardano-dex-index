package fi.spectrumlabs.markets.api.services

import cats.Monad
import cats.effect.Clock
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.markets.api.v1.endpoints.models.HistoryApiQuery
import fi.spectrumlabs.markets.api.v1.models.UserOrderInfo
import tofu.syntax.monadic._
import cats.syntax.traverse._
import fi.spectrumlabs.db.writer.models.db.{DBOrder, Deposit, Redeem, Swap}
import io.circe.Decoder
import io.circe.parser._

import scala.concurrent.duration.{MILLISECONDS, SECONDS}

trait MempoolService[F[_]] {

  def getUserOrders(req: HistoryApiQuery): F[List[UserOrderInfo]]
}

object MempoolService {

  def make[I[+_]: Monad, F[_]: Clock: Monad](implicit redis: Plain[F]): I[MempoolService[F]] =
    new Live[F].pure[I]

  final private class Live[F[_]: Monad: Clock](implicit redis: Plain[F]) extends MempoolService[F] {
    override def getUserOrders(req: HistoryApiQuery): F[List[UserOrderInfo]] =
      req.userPkhs.flatTraverse { key =>
        for {
          curTime       <- Clock[F].realTime(MILLISECONDS)
          depositOrders <- extractOrders[Deposit](Deposit.DepositRedisPrefix, key)
          swapOrders    <- extractOrders[Swap](Swap.SwapRedisPrefix, key)
          redeemOrders  <- extractOrders[Redeem](Redeem.RedeemRedisPrefix, key)
          orders = (depositOrders ++ swapOrders ++ redeemOrders).flatMap(order =>
            UserOrderInfo.fromDbOrder(order, curTime, false, false)
          )
        } yield orders
      }

    def extractOrders[T <: DBOrder](prefix: String, key: String)(implicit decoderT: Decoder[T]): F[List[DBOrder]] =
      redis.get((prefix ++ key).getBytes()).flatMap {
        case Some(ordersRaw) =>
          parse(new String(ordersRaw)) match {
            case Right(parsedRaw) => Decoder[List[T]].decodeJson(parsedRaw).getOrElse(List.empty[DBOrder]).pure[F]
            case Left(_)          => List.empty[DBOrder].pure[F]
          }
        case None => List.empty[DBOrder].pure[F]
      }
  }
}
