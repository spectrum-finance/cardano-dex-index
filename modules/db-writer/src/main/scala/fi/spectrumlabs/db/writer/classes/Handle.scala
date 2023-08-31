package fi.spectrumlabs.db.writer.classes

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Timer
import cats.syntax.option._
import cats.syntax.traverse._
import cats.{Functor, Monad}
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.core.models.domain.Coin
import fi.spectrumlabs.core.models.domain.Coin.Ada
import fi.spectrumlabs.db.writer.classes.ExecutedOrderInfo._
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano._
import fi.spectrumlabs.db.writer.models.db._
import fi.spectrumlabs.db.writer.models.streaming._
import fi.spectrumlabs.db.writer.models.{Output, Transaction}
import fi.spectrumlabs.db.writer.persistence.Persist
import fi.spectrumlabs.db.writer.repositories._
import fi.spectrumlabs.db.writer.services.Tokens
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import mouse.any._
import tofu.logging.{Logging, Logs}
import tofu.syntax.foption.noneF
import tofu.syntax.logging._
import tofu.syntax.monadic._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

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

  def createForPools[F[_]: Monad](
    logs: Logging.Make[F],
    persist: Persist[Pool, F],
    tokens: Tokens[F],
    cardanoConfig: CardanoConfig
  ): Handle[Confirmed[PoolEvent], F] =
    logs
      .forService[Handle[Confirmed[PoolEvent], F]]
      .map(implicit __ => new HandlerForPools[F](persist, tokens, cardanoConfig))

  def createList[A, B, I[_]: Functor, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, List[B]],
    logs: Logs[I, F]
  ): I[Handle[A, F]] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplList[A, B, F](persist, handleLogName))

  def createNel[A, B, F[_]: Monad](persist: Persist[B, F], handleLogName: String)(implicit
    toSchema: ToSchema[A, NonEmptyList[B]],
    logs: Logging.Make[F]
  ): Handle[A, F] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplNel[A, B, F](persist, handleLogName))

  def createOption[A, B, F[_]: Monad](
    persist: Persist[B, F],
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]],
    filter: F[List[B] => List[B]]
  )(implicit logs: Logging.Make[F]): Handle[A, F] =
    logs.forService[Handle[A, F]].map(implicit __ => new ImplOption[A, B, F](persist, handleLogName, toSchema, filter))

  def createOptionForExecutedRedis[A, B: Key: Encoder: Decoder, F[_]: Monad](
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]],
    mempoolTtl: FiniteDuration
  )(implicit logs: Logging.Make[F], redis: Plain[F]): Handle[A, F] =
    logs.forService[Handle[A, F]].map(implicit __ => new RedisDrop[A, B, F](redis, handleLogName, toSchema, mempoolTtl))

  def createExecuted[F[_]: Monad: Timer](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F],
    poolsRepository: PoolsRepository[F]
  )(implicit logs: Logging.Make[F]): Handle[TxEvent, F] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new ExecutedOrdersHandler[F](cardanoConfig, ordersRepository, poolsRepository)
    }

  def createRefunded[F[_]: Monad](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logging.Make[F]): Handle[TxEvent, F] =
    logs.forService[HandlerForRefunds[F]].map { implicit logs =>
      new HandlerForRefunds[F](cardanoConfig, ordersRepository)
    }

  def createForTransaction[F[_]: Monad](
    logs: Logging.Make[F],
    persist: Persist[Transaction, F],
    cardanoConfig: CardanoConfig
  ): Handle[TxEvent, F] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logging =>
      new TransactionHandler[F](persist, cardanoConfig)
    }

  def createForRollbacks[F[_]: Monad](
    ordersRepository: OrdersRepository[F],
    inputsRepository: InputsRepository[F],
    outputsRepository: OutputsRepository[F]
  )(implicit logs: Logging.Make[F]): Handle[TxEvent, F] =
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
    toSchema: ToSchema[A, Option[B]],
    filter: F[List[B] => List[B]]
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      for {
        _ <- info"[$handleLogName] Processing ${in.toString()}"
        elems = in.map(toSchema(_)).toList.flatten
        filtered <- filter.map(_(elems))
        _ <- filtered match {
          case x :: xs =>
            (NonEmptyList.of(x, xs: _*) |> persist.persist)
              .flatMap(r =>
                info"Finished handle [$handleLogName] process for $r elements -> ${x.toString}. Batch size was ${in.size}. ${in.toString()}"
              )
          case Nil =>
            info"Nothing to extract ${in.toString()} [$handleLogName]. Batch contains 0 elements to persist."
        }
      } yield ()
  }

  final private class RedisDrop[A, B: Key: Decoder: Encoder, F[_]: Monad: Logging](
    redis: Plain[F],
    handleLogName: String,
    toSchema: ToSchema[A, Option[B]],
    ttl: FiniteDuration
  ) extends Handle[A, F] {

    def handle(in: NonEmptyList[A]): F[Unit] =
      in.map(toSchema(_)).toList.flatten match {
        case x :: xs =>
          NonEmptyList
            .of(x, xs: _*)
            .traverse { elem =>
              val key = implicitly[Key[B]].getKey(elem)
              redis.get(key.getBytes()).flatMap {
                case Some(raw) =>
                  parse(new String(raw)).flatMap(_.as[List[B]]) match {
                    case Left(value) => info"Failed to parse mempool for $key, ${value.getMessage}"
                    case Right(value) =>
                      val processedList = value.filter { elemInList =>
                        implicitly[Key[B]].getExtendedKey(elem) != implicitly[Key[B]].getExtendedKey(elemInList)
                      }
                      info"Successfully retrieve $key user orders from redis, elem to delete: ${elem.toString}, prev mempool is ${value
                        .toString()} new mempool is: ${processedList.toString()}, original elem is: ${x.toString}" >>
                      redis.setEx(
                        implicitly[Key[B]].getKey(elem).getBytes(),
                        Encoder[List[B]].apply(processedList).toString().getBytes(),
                        ttl
                      )
                  }
                case None => info"No user orders $key is redis. ${implicitly[Key[B]].getKey(elem)}"
              }
            }
            .flatMap(r =>
              info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}. ${in.toString()}"
            )
        case Nil =>
          info"Nothing to extract ${in.toString()} [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  final private class HandlerForPools[F[_]: Monad: Logging](
    persist: Persist[Pool, F],
    tokens: Tokens[F],
    cardanoConfig: CardanoConfig
  ) extends Handle[Confirmed[PoolEvent], F] {

    override def handle(in: NonEmptyList[Confirmed[PoolEvent]]): F[Unit] =
      info"Got next pools ${in.toString()}" >> tokens.get.flatMap { verified =>
        in.map(Pool.toSchemaNew(cardanoConfig).apply)
          .toList
          .filter(_.isVerified(verified))
          .traverse { pool =>
            persist.persist(NonEmptyList.one(pool))
          }
          .void
      }
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
  final private class ExecutedOrdersHandler[F[_]: Monad: Logging: Timer](
    cardanoConfig: CardanoConfig,
    ordersRepository: OrdersRepository[F],
    poolsRepository: PoolsRepository[F]
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
      amountLq      <- userRewardOut.fullTxOutValue.find(deposit.coinLq)
      refundableAda <- userRewardOut.fullTxOutValue.find(Ada)
      poolIn        <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
      poolOut <- tx.txOutputs.find(
        _.fullTxOutValue.contains(deposit.poolId)
      )
      actualX =
        if (deposit.coinX == Coin.Ada) deposit.amountX.value - refundableAda._2 - deposit.exFee.unExFee
        else deposit.amountX.value
    } yield ExecutedDepositOrderInfo(
      amountLq._2,
      userRewardOut.fullTxOutRef,
      poolIn.txInRef,
      poolOut.fullTxOutRef,
      tx.slotNo + cardanoConfig.startTimeInSeconds,
      actualX,
      input.txInRef
    )

    private def tryGetPool(num: Int, poolId: FullTxOutRef, txId: TxId): F[Option[Pool]] =
      poolsRepository.getPoolByOutputId(poolId).flatMap {
        case Some(value) => info"Found pool ${value.toString} ${txId.toString}, $num" as value.some
        case None if num > 0 =>
          info"Nothing for pool for tx ${txId.toString}, $num, retrying" >>
            Timer[F].sleep(1.seconds) >> tryGetPool(num - 1, poolId, txId)
        case _ => info"Nothing for pool for tx ${txId.toString}, $num" >> noneF
      }

    private def resolveSwapOrder(
      swap: Swap,
      input: TxInput,
      tx: AppliedTransaction
    ) =
      for {
        userRewardOut <- OptionT.fromOption(tx.txOutputs.find { txOut =>
          txOut.fullTxOutValue.contains(swap.quote) && checkForPubkey(
            swap.rewardPkh,
            txOut.fullTxOutAddress.addressCredential
          )
        })
        actualQuote <- OptionT.fromOption(userRewardOut.fullTxOutValue.find(swap.quote))
        poolIn      <- OptionT.fromOption(tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption)
        poolOut     <- OptionT.fromOption(tx.txOutputs.find(_.fullTxOutValue.contains(swap.poolId)))
        _ <- OptionT.liftF(
          info"For order ${swap.orderInputId.toString}, ${tx.txId.toString} found executed, going to get pool from db..."
        )
        pool <- OptionT.liftF(tryGetPool(5, poolIn.txInRef, tx.txId))
        actualOutputOpt = pool.map(_.outputAmount(swap.base, swap.baseAmount.value))
        fee             = actualOutputOpt.map(actualOutput => BigDecimal(actualOutput) * swap.exFeePerTokenNum / swap.exFeePerTokenDen)
//        refundable      = swap.originalAdaAmount - fee
        aq = actualOutputOpt match {
          case Some(value) => if (swap.quote == Coin.Ada) value else actualQuote._2
          case None        => actualQuote._2
        }
      } yield ExecutedSwapOrderInfo(
        aq,
        userRewardOut.fullTxOutRef,
        poolIn.txInRef,
        poolOut.fullTxOutRef,
        tx.slotNo + cardanoConfig.startTimeInSeconds,
        fee.map(_.toLong).getOrElse(0L),
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
        x       <- userRewardOut.fullTxOutValue.find(redeem.coinX)
        actualY <- userRewardOut.fullTxOutValue.find(redeem.coinY)
        poolIn  <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
        poolOut <- tx.txOutputs.find(_.fullTxOutValue.contains(redeem.poolId))
        actualX = if (redeem.coinX == Coin.Ada) x._2 - redeem.refundableFee else x._2
      } yield ExecutedRedeemOrderInfo(
        actualX,
        actualY._2,
        userRewardOut.fullTxOutRef,
        poolIn.txInRef,
        poolOut.fullTxOutRef,
        tx.slotNo + cardanoConfig.startTimeInSeconds,
        input.txInRef
      )

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      info"Handle [Executed] got next batch ${in.toString()}" >>
      in.traverse {
        case _: UnAppliedTransaction => unit[F]
        case tx: AppliedTransaction =>
          info"Got next tx ${tx.txId.toString} in executed orders handler" >>
            tx.txInputs.traverse { txInput =>
              info"Got next txInput ${txInput.txInRef}, trying to fetch pending order..." >>
              ordersRepository
                .getOrder(txInput.txInRef)
                .flatMap {
                  case Some(deposit: Deposit) =>
                    info"Going to resolve deposit executed ${deposit.rewardPkh}, ${deposit.orderInputId}" >>
                      resolveDepositOrder(deposit, txInput, tx).traverse { executed =>
                        info"Got executed deposit order ${deposit.orderInputId} for deposit ${deposit.rewardPkh}, order ${deposit.toString}, txIs: ${tx.toString}" >>
                        ordersRepository.updateExecutedDepositOrder(executed)
                      }.void
                  case Some(swap: Swap) =>
                    info"Going to resolve swap executed ${swap.rewardPkh}, ${swap.orderInputId}" >>
                      resolveSwapOrder(swap, txInput, tx).value.flatMap {
                        _.traverse { executed =>
                          info"Got executed swap order ${swap.orderInputId} ${swap.toString} | ${tx.toString} | ${executed.toString}" >>
                          ordersRepository.updateExecutedSwapOrder(executed)
                        }.void
                      }
                  case Some(redeem: Redeem) =>
                    info"Going to resolve redeem executed ${redeem.rewardPkh}, ${redeem.orderInputId}" >>
                      resolveRedeemOrder(redeem, txInput, tx).traverse { executed =>
                        info"Got executed redeem order ${redeem.orderInputId} for pkh ${redeem.rewardPkh}, redeem: ${redeem.toString}, executed: ${executed.toString}, tx: ${tx.toString}" >>
                        ordersRepository.updateExecutedRedeemOrder(executed)
                      }.void
                  case _ => info"Nothing fetched for txInput ${txInput.txInRef}"
                }
                .void
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
            info"Going to check if order exists in refund handler, txInput: ${txInput.toString}" >>
            ordersRepository.getOrder(txInput.txInRef) flatMap {
              case Some(deposit: Deposit) =>
                info"Found deposit order for refund ${deposit.toString}, ${txInput.toString}" >>
                  resolveDepositRefund(deposit, tx).traverse { refundOut =>
                    info"Deposit refund order in tx: ${tx.toString}" >> ordersRepository.refundDepositOrder(
                      deposit.orderInputId,
                      FullTxOutRef.toTxOutRef(refundOut),
                      tx.slotNo + cardanoConfig.startTimeInSeconds
                    )
                  }.void
              case Some(swap: Swap) =>
                info"Found swap order for refund ${swap.toString}, ${txInput.toString}" >>
                  resolveSwapRefund(swap, tx).traverse { refundOut =>
                    info"Swap refund order in tx: ${tx.toString}" >> ordersRepository.refundSwapOrder(
                      swap.orderInputId,
                      FullTxOutRef.toTxOutRef(refundOut),
                      tx.slotNo + cardanoConfig.startTimeInSeconds
                    )
                  }.void
              case Some(redeem: Redeem) =>
                info"Found redeem order for refund ${redeem.toString}, ${txInput.toString}" >>
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
          for {
            outputs <- outputsRepository.getOutputsByTxHash(txId)
            _       <- outputsRepository.dropOutputsByTxHash(txId)
            _ <- outputs.traverse { output =>
              ordersRepository.deleteExecutedDepositOrder(output.ref.value) >>
              ordersRepository.deleteExecutedSwapOrder(output.ref.value) >>
              ordersRepository.deleteExecutedRedeemOrder(output.ref.value)
            }
          } yield ()
        case _ => ().pure[F]
      }.void
  }
}
