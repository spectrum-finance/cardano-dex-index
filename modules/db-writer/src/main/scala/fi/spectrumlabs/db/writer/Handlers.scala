package fi.spectrumlabs.db.writer

import cats.data.NonEmptyList
import cats.effect.Resource
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.db.writer.App.{InitF, RunF, StreamF}
import fi.spectrumlabs.db.writer.classes.Handle
import fi.spectrumlabs.db.writer.config.{CardanoConfig, WriterConfig}
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, Order, PoolEvent}
import fi.spectrumlabs.db.writer.models.db.{DBOrder, Deposit, Redeem, Swap}
import fi.spectrumlabs.db.writer.models.streaming.TxEvent
import fi.spectrumlabs.db.writer.models.{Input, Output}
import fi.spectrumlabs.db.writer.persistence.PersistBundle
import fi.spectrumlabs.db.writer.programs.Handler
import fi.spectrumlabs.db.writer.repositories.{
  InputsRepository,
  OrdersRepository,
  OutputsRepository,
  PoolsRepository,
  TransactionRepository
}
import fs2.Chunk
import tofu.WithContext
import tofu.fs2Instances._
import tofu.logging.Logs
import zio.interop.catz._

object Handlers {

  val TxHandlerName              = "Tx"
  val OrdersHandlerName          = "Order"
  val MempoolOrdersHandlerName   = "MempoolOrder"
  val PoolsHandler               = "PoolsHandler"
  val TxHandleName               = "Transaction"
  val InHandleName               = "Input"
  val ExecutedInput              = "ExecutedInput"
  val OutHandleName              = "Output"
  val DepositHandleName          = "Deposit"
  val DepositRedisHandleName     = "DepositRedis"
  val DepositRedisDropHandleName = "RedisDepositDrop"
  val SwapHandleName             = "Swap"
  val SwapRedisHandleName        = "SwapRedis"
  val SwapRedisDropHandleName    = "RedisSwapDrop"
  val RedeemHandleName           = "Redeem"
  val RedeemRedisHandleName      = "RedeemRedis"
  val RedeemRedisDropHandleName  = "RedisRedeemDrop"
  val PoolHandleName             = "Pool"

  def makeTxHandler(
    config: WriterConfig,
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[RunF],
    inputsRepository: InputsRepository[RunF],
    outputsRepository: OutputsRepository[RunF]
  )(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[TxEvent], StreamF, RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      txn       <- Handle.createForTransaction(logs, transaction, cardanoConfig)
      in        <- Handle.createNel[TxEvent, Input, InitF, RunF](input, InHandleName)
      eIn       <- Handle.createExecuted[InitF, RunF](cardanoConfig, ordersRepository)
      refunds   <- Handle.createRefunded[InitF, RunF](cardanoConfig, ordersRepository)
      out       <- Handle.createNel[TxEvent, Output, InitF, RunF](output, "outputs")
      unApplied <- Handle.createForRollbacks[InitF, RunF](ordersRepository, inputsRepository, outputsRepository)
      implicit0(nelHandlers: NonEmptyList[Handle[TxEvent, RunF]]) = NonEmptyList.of(
        txn,
        in,
        out,
        eIn,
        unApplied,
        refunds
      )
      handler <- Handler.create[TxEvent, StreamF, RunF, Chunk, InitF](config, TxHandlerName)
    } yield handler
  }

  def makeMempoolOrdersHandler(
    config: WriterConfig,
    cardanoConfig: CardanoConfig,
    consumer: Consumer[_, Option[Order], StreamF, RunF]
  )(implicit
    bundle: PersistBundle[RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    implicit val consumerImpl = consumer
    for {
      deposit <- Handle.createOption[Order, Deposit, InitF, RunF](
        depositRedis,
        DepositRedisHandleName,
        Deposit.streamingSchema(cardanoConfig)
      )
      swap <- Handle.createOption[Order, Swap, InitF, RunF](
        swapRedis,
        SwapRedisHandleName,
        Swap.streamingSchema(cardanoConfig)
      )
      redeem <- Handle.createOption[Order, Redeem, InitF, RunF](
        redeemRedis,
        RedeemRedisHandleName,
        Redeem.streamingSchema(cardanoConfig)
      )
      implicit0(nelHandlers: NonEmptyList[Handle[Order, RunF]]) = NonEmptyList.of(deposit, swap, redeem)
      handler <- Handler.create[Order, StreamF, RunF, Chunk, InitF](config, MempoolOrdersHandlerName)
    } yield handler
  }

  def makeOrdersHandler(config: WriterConfig, cardanoConfig: CardanoConfig)(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[Order], StreamF, RunF],
    logs: Logs[InitF, RunF],
    redis: Plain[RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      deposit <- Handle.createOption[Order, Deposit, InitF, RunF](
        deposit,
        DepositHandleName,
        Deposit.streamingSchema(cardanoConfig)
      )
      depositDropRedis <- Handle.createOptionForExecutedRedis[Order, Deposit, InitF, RunF](
        DepositRedisDropHandleName,
        Deposit.streamingSchema(cardanoConfig)
      )
      swap <- Handle.createOption[Order, Swap, InitF, RunF](
        swap,
        SwapHandleName,
        Swap.streamingSchema(cardanoConfig)
      )
      swapDropRedis <- Handle.createOptionForExecutedRedis[Order, Swap, InitF, RunF](
        SwapRedisDropHandleName,
        Swap.streamingSchema(cardanoConfig)
      )
      redeem <- Handle.createOption[Order, Redeem, InitF, RunF](
        redeem,
        RedeemHandleName,
        Redeem.streamingSchema(cardanoConfig)
      )
      redeemDropRedis <- Handle.createOptionForExecutedRedis[Order, Redeem, InitF, RunF](
        RedeemRedisDropHandleName,
        Redeem.streamingSchema(cardanoConfig)
      )
      implicit0(nelHandlers: NonEmptyList[Handle[Order, RunF]]) = NonEmptyList.of(
        deposit,
        swap,
        redeem,
        depositDropRedis,
        swapDropRedis,
        redeemDropRedis
      )
      handler <- Handler.create[Order, StreamF, RunF, Chunk, InitF](config, OrdersHandlerName)
    } yield handler
  }

  def makePoolsHandler(
    config: WriterConfig,
    cardanoConfig: CardanoConfig
  )(implicit
    bundle: PersistBundle[RunF],
    consumer: Consumer[_, Option[Confirmed[PoolEvent]], StreamF, RunF],
    logs: Logs[InitF, RunF]
  ): Resource[InitF, Handler[StreamF]] = Resource.eval {
    import bundle._
    for {
      poolHandler <-
        Handle.createForPools[InitF, RunF](logs, pool, cardanoConfig)
      implicit0(nelHandlers: NonEmptyList[Handle[Confirmed[PoolEvent], RunF]]) = NonEmptyList.of(poolHandler)
      handler <- Handler.create[Confirmed[PoolEvent], StreamF, RunF, Chunk, InitF](config, PoolsHandler)
    } yield handler
  }
}
