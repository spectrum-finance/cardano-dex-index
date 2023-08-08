package fi.spectrumlabs.db.writer

import cats.data.NonEmptyList
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.core.streaming.Consumer
import fi.spectrumlabs.db.writer.classes.Handle
import fi.spectrumlabs.db.writer.config.{CardanoConfig, WriterConfig}
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, Order, PoolEvent}
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}
import fi.spectrumlabs.db.writer.models.streaming.TxEvent
import fi.spectrumlabs.db.writer.models.{Input, Output}
import fi.spectrumlabs.db.writer.persistence.PersistBundle
import fi.spectrumlabs.db.writer.programs.Handler
import fi.spectrumlabs.db.writer.repositories.{InputsRepository, OrdersRepository, OutputsRepository, PoolsRepository}
import fs2.Chunk
import monix.eval.Task
import tofu.fs2Instances._
import tofu.logging.Logging

import scala.concurrent.duration.FiniteDuration

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
    ordersRepository: OrdersRepository[Task],
    inputsRepository: InputsRepository[Task],
    outputsRepository: OutputsRepository[Task],
    poolsRepository: PoolsRepository[Task]
  )(implicit
    bundle: PersistBundle[Task],
    consumer: Consumer[_, Option[TxEvent], App.Stream, Task],
    logs: Logging.Make[Task]
  ): Handler[App.Stream] = {
    import bundle._
    val txn       = Handle.createForTransaction(logs, transaction, cardanoConfig)
    val in        = Handle.createNel[TxEvent, Input, Task](input, InHandleName)
    val eIn       = Handle.createExecuted[Task](cardanoConfig, ordersRepository, poolsRepository)
    val refunds   = Handle.createRefunded[Task](cardanoConfig, ordersRepository)
    val out       = Handle.createNel[TxEvent, Output, Task](output, "outputs")
    val unApplied = Handle.createForRollbacks[Task](ordersRepository, inputsRepository, outputsRepository)
    implicit val nelHandlers: NonEmptyList[Handle[TxEvent, Task]] = NonEmptyList.of(
      out,
      eIn,
      unApplied,
      refunds
    )
    Handler.create[TxEvent, App.Stream, Task, Chunk](config, TxHandlerName)
  }

  def makeMempoolOrdersHandler(
    config: WriterConfig,
    cardanoConfig: CardanoConfig,
    consumer: Consumer[_, Option[Order], App.Stream, Task]
  )(implicit
    bundle: PersistBundle[Task],
    logs: Logging.Make[Task]
  ): Handler[App.Stream] = {
    import bundle._
    implicit val consumerImpl = consumer
    val deposit = Handle.createOption[Order, Deposit, Task](
      depositRedis,
      DepositRedisHandleName,
      Deposit.streamingSchema(cardanoConfig)
    )
    val swap = Handle.createOption[Order, Swap, Task](
      swapRedis,
      SwapRedisHandleName,
      Swap.streamingSchema(cardanoConfig)
    )
    val redeem = Handle.createOption[Order, Redeem, Task](
      redeemRedis,
      RedeemRedisHandleName,
      Redeem.streamingSchema(cardanoConfig)
    )
    implicit val nelHandlers: NonEmptyList[Handle[Order, Task]] = NonEmptyList.of(deposit, swap, redeem)
    Handler.create[Order, App.Stream, Task, Chunk](config, MempoolOrdersHandlerName)
  }

  def makeOrdersHandler(config: WriterConfig, cardanoConfig: CardanoConfig, mempoolTtl: FiniteDuration)(implicit
    bundle: PersistBundle[Task],
    consumer: Consumer[_, Option[Order], App.Stream, Task],
    logs: Logging.Make[Task],
    redis: Plain[Task]
  ): Handler[App.Stream] = {
    import bundle._
    val deposit1 = Handle.createOption[Order, Deposit, Task](
      deposit,
      DepositHandleName,
      Deposit.streamingSchema(cardanoConfig)
    )
    val depositDropRedis = Handle.createOptionForExecutedRedis[Order, Deposit, Task](
      DepositRedisDropHandleName,
      Deposit.streamingSchema(cardanoConfig),
      mempoolTtl
    )
    val swap1 = Handle.createOption[Order, Swap, Task](
      swap,
      SwapHandleName,
      Swap.streamingSchema(cardanoConfig)
    )
    val swapDropRedis = Handle.createOptionForExecutedRedis[Order, Swap, Task](
      SwapRedisDropHandleName,
      Swap.streamingSchema(cardanoConfig),
      mempoolTtl
    )
    val redeem1 = Handle.createOption[Order, Redeem, Task](
      redeem,
      RedeemHandleName,
      Redeem.streamingSchema(cardanoConfig)
    )
    val redeemDropRedis = Handle.createOptionForExecutedRedis[Order, Redeem, Task](
      RedeemRedisDropHandleName,
      Redeem.streamingSchema(cardanoConfig),
      mempoolTtl
    )
    implicit val nelHandlers: NonEmptyList[Handle[Order, Task]] = NonEmptyList.of(
      deposit1,
      swap1,
      redeem1,
      depositDropRedis,
      swapDropRedis,
      redeemDropRedis
    )
    Handler.create[Order, App.Stream, Task, Chunk](config, OrdersHandlerName)
  }

  def makePoolsHandler(
    config: WriterConfig,
    cardanoConfig: CardanoConfig
  )(implicit
    bundle: PersistBundle[Task],
    consumer: Consumer[_, Option[Confirmed[PoolEvent]], App.Stream, Task],
    logs: Logging.Make[Task]
  ): Handler[App.Stream] = {
    import bundle._
    val poolHandler                                                            = Handle.createForPools[Task](logs, pool, cardanoConfig)
    implicit val nelHandlers: NonEmptyList[Handle[Confirmed[PoolEvent], Task]] = NonEmptyList.of(poolHandler)
    Handler.create[Confirmed[PoolEvent], App.Stream, Task, Chunk](config, PoolsHandler)
  }
}
