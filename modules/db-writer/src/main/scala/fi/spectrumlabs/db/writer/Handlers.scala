package fi.spectrumlabs.db.writer

import cats.data.NonEmptyList
import cats.effect.Resource
import fi.spectrumlabs.core.models.Tx
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.db.writer.App.{InitF, RunF, StreamF}
import fi.spectrumlabs.db.writer.classes.Handle
import fi.spectrumlabs.db.writer.config.WriterConfig
import fi.spectrumlabs.db.writer.models.cardano.{Action, Confirmed, DepositAction, Order, PoolEvent, RedeemAction, SwapAction}
import fi.spectrumlabs.db.writer.models.db.{Deposit, ExecutedDeposit, ExecutedRedeem, ExecutedSwap, Pool, Redeem, Swap}
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, ExecutedOrderEvent}
import fi.spectrumlabs.db.writer.models.{Input, Output, Redeemer, Transaction}
import fi.spectrumlabs.db.writer.persistence.PersistBundle
import fi.spectrumlabs.db.writer.programs.Handler
import fs2.Chunk
import tofu.WithContext
import tofu.fs2Instances._
import tofu.logging.Logs
import zio.interop.catz._

object Handlers {

  val TxHandlerName             = "Tx"
  val OrdersHandlerName         = "Order"
  val PoolsHandler              = "PoolsHandler"
  val TxHandleName              = "Transaction"
  val InHandleName              = "Input"
  val OutHandleName             = "Output"
  val ReedHandleName            = "Redeemer"
  val DepositHandleName         = "Deposit"
  val SwapHandleName            = "Swap"
  val RedeemHandleName          = "Redeem"
  val PoolHandleName            = "Pool"

  def makeTxHandler(config: WriterConfig)(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[AppliedTransaction], StreamF, RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      txn  <- Handle.createOne[AppliedTransaction, Transaction, InitF, RunF](transaction, TxHandleName)
      in   <- Handle.createNel[AppliedTransaction, Input, InitF, RunF](input, InHandleName)
      out  <- Handle.createNel[AppliedTransaction, Output, InitF, RunF](output, OutHandleName)
      //reed <- Handle.createList[Tx, Redeemer, InitF, RunF](redeemer, ReedHandleName)
      implicit0(nelHandlers: NonEmptyList[Handle[AppliedTransaction, RunF]]) = NonEmptyList.of(txn, in, out)
      handler <- Handler.create[AppliedTransaction, StreamF, RunF, Chunk, InitF](config, TxHandlerName)
    } yield handler
  }

  def makeOrdersHandler(config: WriterConfig)(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[Order], StreamF, RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      deposit <-
        Handle.createOption[Order, Deposit, InitF, RunF](deposit, DepositHandleName)
      swap   <- Handle.createOption[Order, Swap, InitF, RunF](swap, SwapHandleName)
      redeem <- Handle.createOption[Order, Redeem, InitF, RunF](redeem, RedeemHandleName)
      implicit0(nelHandlers: NonEmptyList[Handle[Order, RunF]]) = NonEmptyList.of(deposit, swap, redeem)
      handler <- Handler.create[Order, StreamF, RunF, Chunk, InitF](config, OrdersHandlerName)
    } yield handler
  }

  def makePoolsHandler(config: WriterConfig)(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[Confirmed[PoolEvent]], StreamF, RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      poolHandler <- Handle.createOne[Confirmed[PoolEvent], Pool, InitF, RunF](pool, PoolHandleName)
      implicit0(nelHandlers: NonEmptyList[Handle[Confirmed[PoolEvent], RunF]]) = NonEmptyList.of(poolHandler)
      handler <- Handler.create[Confirmed[PoolEvent], StreamF, RunF, Chunk, InitF](config, PoolsHandler)
    } yield handler
  }

}
