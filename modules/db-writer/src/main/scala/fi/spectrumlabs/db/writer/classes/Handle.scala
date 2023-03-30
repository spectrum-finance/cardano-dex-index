package fi.spectrumlabs.db.writer.classes

import cats.data.{NonEmptyList, OptionT}
import cats.syntax.flatMap._
import cats.syntax.applicative._
import cats.syntax.traverse._
import cats.syntax.functor._
import cats.{Functor, Monad}
import fi.spectrumlabs.db.writer.models.{ExecutedInput, Output, Transaction}
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent, UnAppliedTransaction}
import fi.spectrumlabs.db.writer.persistence.Persist
import fi.spectrumlabs.db.writer.repositories.{
  InputsRepository,
  OrdersRepository,
  OutputsRepository,
  PoolsRepository,
  TransactionRepository
}
import mouse.any._
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import cats.syntax.traverse._
import fi.spectrumlabs.db.writer.classes.OrdersInfo.{ExecutedDepositOrderInfo, ExecutedRedeemOrderInfo, ExecutedSwapOrderInfo}
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{
  AddressCredential,
  Confirmed,
  FullTxOutRef,
  FullTxOutValue,
  PoolEvent,
  PubKeyAddressCredential,
  ScriptAddressCredential,
  TxInput
}
import fi.spectrumlabs.db.writer.models.db.{Deposit, Pool, Redeem, Swap}

/** Keeps both ToSchema from A to B and Persist for B.
  * Contains evidence that A can be mapped into B and B can be persisted.
  *
  * Takes batch of T elements, maps them using ToSchema, persists them using Persist
  */

trait Handle[T, F[_]] {
  def handle(in: NonEmptyList[T]): F[Unit]
}

object Handle {

  def createOne[A, B, I[_]: Functor, F[_]: Monad](
    persist: Persist[B, F],
    handleLogName: String
  )(implicit toSchema: ToSchema[A, B], logs: Logs[I, F]): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOne[A, B, F](persist, handleLogName))

  def createForOutputs[I[_]: Functor, F[_]: Monad](
    poolsRepository: PoolsRepository[F],
    transactionRepo: TransactionRepository[F],
    logs: Logs[I, F],
    persist: Persist[Output, F]
  ): I[Handle[TxEvent, F]] =
    logs
      .forService[Handle[Output, F]]
      .map(implicit __ => new OutputsHandler[F](poolsRepository, transactionRepo, "outputsHandler", persist))

  def createForPools[I[_]: Functor, F[_]: Monad](
    poolsRepository: PoolsRepository[F],
    transactionRepo: TransactionRepository[F],
    logs: Logs[I, F],
    persist: Persist[Pool, F],
    cardanoConfig: CardanoConfig
  ): I[Handle[Confirmed[PoolEvent], F]] =
    logs
      .forService[Handle[Confirmed[PoolEvent], F]]
      .map(implicit __ =>
        new HandlerForPools[F](poolsRepository, transactionRepo, "poolsHandler", persist, cardanoConfig)
      )

  def createList[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, List[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplList[A, B, F](persist, handleLogName))

  def createNel[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, NonEmptyList[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplNel[A, B, F](persist, handleLogName))

  def createOption[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, Option[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOption[A, B, F](persist, handleLogName))

  def createOptionExcl[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(
    toSchema: ToSchema[A, Option[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] = {
    implicit val implSchema = toSchema
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOption[A, B, F](persist, handleLogName))
  }

  def createExecuted[I[_]: Functor, F[_]: Monad](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new ExecutedOrdersHandler[F](cardanoConfig, ordersRepository, "executedOrders")
    }

  def createForTransaction[I[_]: Functor, F[_]: Monad](
    logs: Logs[I, F],
    poolsRepo: PoolsRepository[F],
    persist: Persist[Transaction, F],
    cardanoConfig: CardanoConfig
  ): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logging =>
      new TransactionHandler[F]("transactions", poolsRepo, persist, cardanoConfig)
    }

  def createForRollbacks[I[_]: Functor, F[_]: Monad](
    ordersRepository: OrdersRepository[F],
    inputsRepository: InputsRepository[F],
    outputsRepository: OutputsRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new HandlerForUnAppliedTxs[F](ordersRepository, inputsRepository, outputsRepository, "unAppliedOrders")
    }

  private final class ImplOne[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, B]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      (in.map(toSchema(_)) |> persist.persist)
        .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")

  }

  private final class ImplList[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, List[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.toList.flatMap(toSchema(_)) match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist. ${in.toList.toString()}"
      }
  }

  private final class ImplNel[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, NonEmptyList[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.flatMap(toSchema(_)).toList match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  private final class ImplOption[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, Option[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.map(toSchema(_)).toList.flatten match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}. ${in.toString()}")
        case Nil =>
          info"Nothing to extract ${in.toString()} [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  final private class HandlerForPools[F[_]: Monad: Logging](
    poolsRepository: PoolsRepository[F],
    transactionRepo: TransactionRepository[F],
    handleLogName: String,
    persist: Persist[Pool, F],
    cardanoConfig: CardanoConfig
  ) extends Handle[Confirmed[PoolEvent], F] {

    override def handle(in: NonEmptyList[Confirmed[PoolEvent]]): F[Unit] =
      in.map(Pool.toSchemaNew(cardanoConfig).apply)
        .toList
        .filter(pool => cardanoConfig.supportedPools.contains(pool.id))
        .traverse { pool =>
          persist.persist(NonEmptyList.one(pool)) >> (for {
            tx <- OptionT(transactionRepo.getTxByHash(pool.outputId.txOutRefId.getTxId))
            _ <-
              OptionT.liftF(
                poolsRepository
                  .updatePoolTimestamp(
                    FullTxOutRef.fromTxOutRef(pool.outputId),
                    tx.timestamp
                  )
              )
          } yield ()).value
        }
        .void
  }

  private final class TransactionHandler[F[_]: Monad: Logging](
    handleLogName: String,
    poolRepo: PoolsRepository[F], //only for first iteration, change pool model in haskell part
    persist: Persist[Transaction, F],
    cardanoConfig: CardanoConfig
  ) extends Handle[TxEvent, F] {

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      persist
        .persist(
          in.map(Transaction.toSchemaNew.apply)
            .map(prevTx => prevTx.copy(timestamp = prevTx.timestamp + cardanoConfig.startTimeInSeconds))
        )
        .void >> in.traverse {
        case (tx: AppliedTransaction) =>
          tx.txOutputs.traverse { output =>
            (OptionT(poolRepo.getPoolByOutputId(output.fullTxOutRef)) >> OptionT.liftF(
              poolRepo.updatePoolTimestamp(output.fullTxOutRef, tx.slotNo + cardanoConfig.startTimeInSeconds)
            )).value.void
          }.void
        case _ => ().pure[F]
      }.void
  }

  private final class OutputsHandler[F[_]: Monad: Logging](
    poolsRepository: PoolsRepository[F],
    transactionRepo: TransactionRepository[F],
    handleLogName: String,
    persist: Persist[Output, F]
  ) extends Handle[TxEvent, F] {

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] = {
      in.flatMap(Output.toSchemaNew.apply).toList traverse { elem =>
        val outputsList = NonEmptyList.one(elem)
        persist.persist(outputsList) >> (for {
          ref <- OptionT.fromOption(FullTxOutRef.fromString(elem.ref.value).toOption)
          _   <- OptionT(poolsRepository.getPoolByOutputId(ref))
          tx  <- OptionT(transactionRepo.getTxByHash(elem.txHash.value))
          _   <- OptionT.liftF(poolsRepository.updatePoolTimestamp(ref, tx.timestamp))
        } yield ()).value.void
      }
    }.void
  }

  // draft for executed inputs handler
  private final class ExecutedOrdersHandler[F[_]: Monad: Logging](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F],
    handleLogName: String
  ) extends Handle[TxEvent, F] {

    def checkForPubkey(pubkey2check: String, addressCredential: AddressCredential): Boolean =
      addressCredential match {
        case PubKeyAddressCredential(contents, tag) => contents.getPubKeyHash == pubkey2check
        case _                                      => false
      }

    private def resolveDepositOrder(
      deposit: Deposit,
      input: TxInput,
      tx: AppliedTransaction
    ): Option[ExecutedDepositOrderInfo] = for {
      userRewardOut <-
        tx.txOutputs.find { txOut =>
          txOut.fullTxOutValue.contains(deposit.coinLq) && checkForPubkey(
            deposit.rewardPkh,
            txOut.fullTxOutAddress.addressCredential
          )
        }
      amountLq <- userRewardOut.fullTxOutValue.find(deposit.coinLq)
      poolIn   <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
      poolOut <- tx.txOutputs.find(
                   _.fullTxOutValue.contains(deposit.poolId)
                 )
    } yield ExecutedDepositOrderInfo(
      amountLq._2,
      userRewardOut.fullTxOutRef,
      poolIn.txInRef,
      poolOut.fullTxOutRef,
      tx.slotNo + cardanoConfig.startTimeInSeconds,
      input.txInRef
    )

    private def resolveSwapOrder(swap: Swap, input: TxInput, tx: AppliedTransaction): Option[ExecutedSwapOrderInfo] = for {
      userRewardOut <- tx.txOutputs.find { txOut =>
                         txOut.fullTxOutValue.contains(swap.quote) && checkForPubkey(
                           swap.rewardPkh,
                           txOut.fullTxOutAddress.addressCredential
                         )
                       }
      actualQuote <- userRewardOut.fullTxOutValue.find(swap.quote)
      poolIn      <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
      poolOut     <- tx.txOutputs.find(_.fullTxOutValue.contains(swap.poolId))
    } yield ExecutedSwapOrderInfo(
      actualQuote._2,
      userRewardOut.fullTxOutRef,
      poolIn.txInRef,
      poolOut.fullTxOutRef,
      tx.slotNo + cardanoConfig.startTimeInSeconds,
      input.txInRef
    )

    private def resolveRedeemOrder(redeem: Redeem, input: TxInput, tx: AppliedTransaction): Option[ExecutedRedeemOrderInfo] =
      for {
        userRewardOut <- tx.txOutputs.find { txOut =>
                           txOut.fullTxOutValue.contains(redeem.coinX) && txOut.fullTxOutValue.contains(
                             redeem.coinY
                           ) && checkForPubkey(
                             redeem.rewardPkh.getPubKeyHash,
                             txOut.fullTxOutAddress.addressCredential
                           )
                         }
        actualX <- userRewardOut.fullTxOutValue.find(redeem.coinX)
        actualY <- userRewardOut.fullTxOutValue.find(redeem.coinY)
        poolIn  <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
        poolOut <- tx.txOutputs.find(_.fullTxOutValue.contains(redeem.poolId))
      } yield ExecutedRedeemOrderInfo(
        actualX._2,
        actualY._2,
        userRewardOut.fullTxOutRef,
        poolIn.txInRef,
        poolOut.fullTxOutRef,
        tx.slotNo + cardanoConfig.startTimeInSeconds,
        input.txInRef
      )

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      in.traverse {
        case _: UnAppliedTransaction => ().pure[F]
        case tx: AppliedTransaction =>
          tx.txInputs.traverse { txInput =>
            ordersRepository.getOrder(txInput.txInRef) flatMap {
              case Some(deposit: Deposit) =>
                resolveDepositOrder(deposit, txInput, tx).traverse { deposit =>
                  info"Deposit order in tx: ${tx.toString}" >> ordersRepository.updateExecutedDepositOrder(deposit)
                }.void
              case Some(swap: Swap) =>
                resolveSwapOrder(swap, txInput, tx).traverse { swap =>
                  info"Swap order in tx: ${tx.toString}" >> ordersRepository.updateExecutedSwapOrder(swap)
                }.void
              case Some(redeem: Redeem) =>
                resolveRedeemOrder(redeem, txInput, tx).traverse { redeem =>
                  info"Redeem order in tx: ${tx.toString}" >> ordersRepository.updateExecutedRedeemOrder(redeem)
                }.void
              case _ => ().pure[F]
            }
          }.void
      }.void
  }

  final private class HandlerForUnAppliedTxs[F[_]: Monad: Logging](
    ordersRepository: OrdersRepository[F],
    inputsRepository: InputsRepository[F],
    outputsRepository: OutputsRepository[F],
    handleLogName: String
  ) extends Handle[TxEvent, F] {

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      in.traverse {
        case UnAppliedTransaction(txId) =>
          inputsRepository.dropInputsByTxHash(txId) >> (for {
            outputs <- outputsRepository.getOutputsByTxHash(txId)
            _       <- outputsRepository.dropOutputsByTxHash(txId)
            _ <- outputs.traverse { output =>
                   ordersRepository.deleteExecutedDepositOrder(output.ref.value) >>
                   ordersRepository.deleteExecutedSwapOrder(output.ref.value) >>
                   ordersRepository.deleteExecutedRedeemOrder(output.ref.value)
                 }
          } yield ())
        case _ => ().pure[F]
      }.void
  }
}
