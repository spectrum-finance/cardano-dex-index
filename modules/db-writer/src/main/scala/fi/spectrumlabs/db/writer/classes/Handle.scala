package fi.spectrumlabs.db.writer.classes

import cats.data.{NonEmptyList, OptionT}
import cats.syntax.traverse._
import tofu.syntax.monadic._
import cats.{Functor, Monad}
import cats.syntax.option._
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
import io.circe.parser._
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.db.writer.classes.ExecutedOrderInfo.{
  ExecutedDepositOrderInfo,
  ExecutedRedeemOrderInfo,
  ExecutedSwapOrderInfo
}
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
import io.circe.{Decoder, Encoder}

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
    logs: Logs[I, F],
    persist: Persist[Output, F]
  ): I[Handle[TxEvent, F]] =
    logs
      .forService[Handle[Output, F]]
      .map(implicit __ => new OutputsHandler[F](persist))

  def createForPools[I[_]: Functor, F[_]: Monad](
    logs: Logs[I, F],
    persist: Persist[Pool, F],
    cardanoConfig: CardanoConfig
  ): I[Handle[Confirmed[PoolEvent], F]] =
    logs
      .forService[Handle[Confirmed[PoolEvent], F]]
      .map(implicit __ => new HandlerForPools[F](persist, cardanoConfig))

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

  def createOption[A, B, I[_]: Functor, F[_]: Monad](
    persist: Persist[B, F],
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]]
  )(implicit logs: Logs[I, F]): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOption[A, B, F](persist, handleLogName, toSchema))

  def createOptionForExecutedRedis[A, B: Key: Encoder: Decoder, I[_]: Functor, F[_]: Monad](
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]]
  )(implicit logs: Logs[I, F], redis: Plain[F]): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new RedisDrop[A, B, F](redis, handleLogName, toSchema))

  def createExecuted[I[_]: Functor, F[_]: Monad](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new ExecutedOrdersHandler[F](cardanoConfig, ordersRepository)
    }

  def createRefunded[I[_]: Functor, F[_]: Monad](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[HandlerForRefunds[F]].map { implicit logs =>
      new HandlerForRefunds[F](cardanoConfig, ordersRepository)
    }

  def createForTransaction[I[_]: Functor, F[_]: Monad](
    logs: Logs[I, F],
    persist: Persist[Transaction, F],
    cardanoConfig: CardanoConfig
  ): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logging =>
      new TransactionHandler[F](persist, cardanoConfig)
    }

  def createForRollbacks[I[_]: Functor, F[_]: Monad](
    ordersRepository: OrdersRepository[F],
    inputsRepository: InputsRepository[F],
    outputsRepository: OutputsRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new HandlerForUnAppliedTxs[F](ordersRepository, inputsRepository, outputsRepository, "unAppliedOrders")
    }

  final private class ImplOne[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, B]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      (in.map(toSchema(_)) |> persist.persist)
        .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")

  }

  final private class ImplList[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
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

  final private class ImplNel[A, B, F[_]: Monad: Logging](persist: Persist[B, F], handleLogName: String)(implicit
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

  final private class ImplOption[A, B, F[_]: Monad: Logging](
    persist: Persist[B, F],
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      info"Going to test: ${in.toString()} against schema in $handleLogName" >> (in
        .map(toSchema(_))
        .toList
        .flatten match {
        case x :: xs =>
          (NonEmptyList.of(x, xs: _*) |> persist.persist)
            .flatMap(r =>
              info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}. ${in.toString()}"
            )
        case Nil =>
          info"Nothing to extract ${in.toString()} [$handleLogName]. Batch contains 0 elements to persist."
      })
  }

  final private class RedisDrop[A, B: Key: Decoder: Encoder, F[_]: Monad: Logging](
    redis: Plain[F],
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.map(toSchema(_)).toList.flatten match {
        case x :: xs =>
          (
            NonEmptyList
              .of(x, xs: _*)
              .traverse(elem =>
                redis.get(implicitly[Key[B]].getKey(elem).getBytes()).flatMap {
                  case Some(listValuesRaw) =>
                    parse(new String(listValuesRaw)) match {
                      case Left(value) =>
                        info"Trying to parse value from redis by key ${implicitly[Key[B]].getKey(elem)}. Error: ${value.message}"
                      case Right(value) =>
                        Decoder[List[B]].decodeJson(value) match {
                          case Left(value) =>
                            info"Trying to decode json value from redis by key ${implicitly[Key[B]]
                              .getKey(elem)}. Error: ${value.message}"
                          case Right(list) =>
                            val processedList = list.filter(elemInList => elemInList != elem)
                            info"Successfully retrieve user orders from redis" >>
                            redis.set(
                              implicitly[Key[B]].getKey(elem).getBytes(),
                              Encoder[List[B]].apply(processedList).toString().getBytes()
                            )
                        }
                    }
                  case None => info"No user orders is redis. ${implicitly[Key[B]].getKey(elem)}"
                }
              )
              .flatMap(r =>
                info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}. ${in.toString()}"
              )
          )
        case Nil =>
          info"Nothing to extract ${in.toString()} [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  final private class HandlerForPools[F[_]: Monad: Logging](
    persist: Persist[Pool, F],
    cardanoConfig: CardanoConfig
  ) extends Handle[Confirmed[PoolEvent], F] {

    override def handle(in: NonEmptyList[Confirmed[PoolEvent]]): F[Unit] =
      info"going to test pool: ${in.toString()}" >>
      info"cardanoConfig.supportedPools: ${cardanoConfig.supportedPools}" >>
      in.map(Pool.toSchemaNew(cardanoConfig).apply)
        .toList
        .filter(pool => cardanoConfig.supportedPools.contains(pool.id.value))
        .traverse { pool =>
          persist.persist(NonEmptyList.one(pool))
        }
        .void
  }

  final private class TransactionHandler[F[_]: Monad: Logging](
    persist: Persist[Transaction, F],
    cardanoConfig: CardanoConfig
  ) extends Handle[TxEvent, F] {

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      persist
        .persist(
          in.map(Transaction.toSchemaNew.apply)
            .map(prevTx => prevTx.copy(timestamp = prevTx.timestamp + cardanoConfig.startTimeInSeconds))
        )
        .void
  }

  final private class OutputsHandler[F[_]: Monad: Logging](
    persist: Persist[Output, F]
  ) extends Handle[TxEvent, F] {

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] = {
      in.flatMap(Output.toSchemaNew.apply).toList traverse { elem =>
        val outputsList = NonEmptyList.one(elem)
        persist.persist(outputsList)
      }
    }.void
  }

  // draft for executed inputs handler
  final private class ExecutedOrdersHandler[F[_]: Monad: Logging](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
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

    private def resolveSwapOrder(swap: Swap, input: TxInput, tx: AppliedTransaction): Option[ExecutedSwapOrderInfo] =
      for {
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

    private def resolveRedeemOrder(
      redeem: Redeem,
      input: TxInput,
      tx: AppliedTransaction
    ): Option[ExecutedRedeemOrderInfo] =
      for {
        userRewardOut <- tx.txOutputs.find { txOut =>
          txOut.fullTxOutValue.contains(redeem.coinX) && txOut.fullTxOutValue.contains(
            redeem.coinY
          ) && checkForPubkey(
            redeem.rewardPkh,
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
        case _: UnAppliedTransaction => unit[F]
        case tx: AppliedTransaction =>
          tx.txInputs.traverse { txInput =>
            info"Got next txInput ${txInput.txInRef}, trying to fetch pending order..." >>
            ordersRepository.getOrder(txInput.txInRef).flatMap {
              case Some(deposit: Deposit) =>
                info"Going to resolve deposit executed ${deposit.rewardPkh}, ${deposit.orderInputId}" >>
                resolveDepositOrder(deposit, txInput, tx).traverse { executed =>
                  info"Got executed deposit order ${deposit.orderInputId} for pkh ${deposit.rewardPkh}" >>
                  ordersRepository.updateExecutedDepositOrder(executed)
                }.void
              case Some(swap: Swap) =>
                info"Going to resolve swap executed ${swap.rewardPkh}, ${swap.orderInputId}" >>
                resolveSwapOrder(swap, txInput, tx).traverse { executed =>
                  info"Got executed swap order ${swap.orderInputId} for pkh ${swap.rewardPkh}" >>
                  ordersRepository.updateExecutedSwapOrder(executed)
                }.void
              case Some(redeem: Redeem) =>
                info"Going to resolve redeem executed ${redeem.rewardPkh}, ${redeem.orderInputId}" >>
                resolveRedeemOrder(redeem, txInput, tx).traverse { executed =>
                  info"Got executed redeem order ${redeem.orderInputId} for pkh ${redeem.rewardPkh}" >>
                  ordersRepository.updateExecutedRedeemOrder(executed)
                }.void
              case _ => info"Nothing fetched for txInput ${txInput.txInRef}"
            }.void
          }.void
      }.void
  }

  final private class HandlerForRefunds[F[_]: Monad: Logging](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
  ) extends Handle[TxEvent, F] {

    def checkForPubkey(pubkey2check: String, addressCredential: AddressCredential): Boolean =
      addressCredential match {
        case PubKeyAddressCredential(contents, tag) => contents.getPubKeyHash == pubkey2check
        case _                                      => false
      }

    private def resolveDepositRefund(
      deposit: Deposit,
      tx: AppliedTransaction
    ): Option[FullTxOutRef] =
      if (tx.txOutputs.find(_.fullTxOutValue.contains(deposit.poolId)).isEmpty)
        tx.txOutputs
          .find { txOut =>
            txOut.fullTxOutValue
              .contains(deposit.coinX) && txOut.fullTxOutValue.contains(deposit.coinY) && checkForPubkey(
              deposit.rewardPkh,
              txOut.fullTxOutAddress.addressCredential
            )
          }
          .map(_.fullTxOutRef)
      else none

    private def resolveSwapRefund(
      swap: Swap,
      tx: AppliedTransaction
    ): Option[FullTxOutRef] =
      if (tx.txOutputs.find(_.fullTxOutValue.contains(swap.poolId)).isEmpty)
        tx.txOutputs
          .find { txOut =>
            txOut.fullTxOutValue.contains(swap.base) && checkForPubkey(
              swap.rewardPkh,
              txOut.fullTxOutAddress.addressCredential
            )
          }
          .map(_.fullTxOutRef)
      else none

    private def resolveRedeemRefund(
      redeem: Redeem,
      tx: AppliedTransaction
    ): Option[FullTxOutRef] =
      if (tx.txOutputs.find(_.fullTxOutValue.contains(redeem.poolId)).isEmpty)
        tx.txOutputs
          .find { txOut =>
            txOut.fullTxOutValue.contains(redeem.coinLq) && checkForPubkey(
              redeem.rewardPkh,
              txOut.fullTxOutAddress.addressCredential
            )
          }
          .map(_.fullTxOutRef)
      else none

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      in.traverse {
        case _: UnAppliedTransaction => ().pure[F]
        case tx: AppliedTransaction =>
          tx.txInputs.traverse { txInput =>
            ordersRepository.getOrder(txInput.txInRef) flatMap {
              case Some(deposit: Deposit) =>
                resolveDepositRefund(deposit, tx).traverse { refundOut =>
                  info"Deposit refund order in tx: ${tx.toString}" >> ordersRepository.refundDepositOrder(
                    deposit.orderInputId,
                    FullTxOutRef.toTxOutRef(refundOut),
                    tx.slotNo + cardanoConfig.startTimeInSeconds
                  )
                }.void
              case Some(swap: Swap) =>
                resolveSwapRefund(swap, tx).traverse { refundOut =>
                  info"Swap refund order in tx: ${tx.toString}" >> ordersRepository.refundSwapOrder(
                    swap.orderInputId,
                    FullTxOutRef.toTxOutRef(refundOut),
                    tx.slotNo + cardanoConfig.startTimeInSeconds
                  )
                }.void
              case Some(redeem: Redeem) =>
                resolveRedeemRefund(redeem, tx).traverse { refundOut =>
                  info"Redeem refund order in tx: ${tx.toString}" >> ordersRepository.refundRedeemOrder(
                    redeem.orderInputId,
                    FullTxOutRef.toTxOutRef(refundOut),
                    tx.slotNo + cardanoConfig.startTimeInSeconds
                  )
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
