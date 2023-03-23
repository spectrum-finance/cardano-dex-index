package fi.spectrumlabs.db.writer.classes

import cats.data.{NonEmptyList, OptionT}
import cats.syntax.flatMap._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.{Functor, Monad}
import fi.spectrumlabs.db.writer.models.ExecutedInput
import fi.spectrumlabs.db.writer.models.streaming.{AppliedTransaction, TxEvent, UnAppliedTransaction}
import fi.spectrumlabs.db.writer.persistence.Persist
import fi.spectrumlabs.db.writer.repositories.{InputsRepository, OrdersRepository, OutputsRepository}
import mouse.any._
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import cats.syntax.traverse._
import fi.spectrumlabs.db.writer.classes.OrdersInfo.{DepositOrderInfo, RedeemOrderInfo, SwapOrderInfo}
import fi.spectrumlabs.db.writer.models.cardano.{
  AddressCredential,
  FullTxOutValue,
  PubKeyAddressCredential,
  ScriptAddressCredential,
  TxInput
}
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}

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

  def createExecuted[I[_]: Functor, F[_]: Monad](
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logs[I, F]): I[Handle[TxEvent, F]] =
    logs.forService[ExecutedOrdersHandler[F]].map { implicit logs =>
      new ExecutedOrdersHandler[F](ordersRepository, "executedOrders")
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
            .flatMap(r => info"Finished handle [$handleLogName] process for $r elements. Batch size was ${in.size}.")
        case Nil =>
          info"Nothing to extract [$handleLogName]. Batch contains 0 elements to persist."
      }
  }

  // draft for executed inputs handler
  private final class ExecutedOrdersHandler[F[_]: Monad: Logging](
    ordersRepository: OrdersRepository[F],
    handleLogName: String
  ) extends Handle[TxEvent, F] {

    def flattenValues(fullTxOutValue: FullTxOutValue): Map[String, Long] =
      fullTxOutValue.getValue.flatMap { values =>
        values.tokens.map(tokenValue =>
          s"${values.curSymbol.unCurrencySymbol}.${tokenValue.tokenName.unTokenName}" -> tokenValue.value
        )
      }.toMap

    def checkForPubkey(pubkey2check: String, addressCredential: AddressCredential): Boolean =
      addressCredential match {
        case ScriptAddressCredential(contents, tag) => false
        case PubKeyAddressCredential(contents, tag) => contents.getPubKeyHash == pubkey2check
      }

    private def resolveDepositOrder(
      deposit: Deposit,
      input: TxInput,
      tx: AppliedTransaction
    ): F[Option[DepositOrderInfo]] = (for {
      userRewardOut <-
        OptionT.fromOption(tx.txOutputs.find { txOut =>
          val fValues = flattenValues(txOut.fullTxOutValue)
          fValues.contains(deposit.coinLq.value) && checkForPubkey(
            deposit.rewardPkh,
            txOut.fullTxOutAddress.addressCredential
          )
        })
      amountLq <- OptionT.fromOption(flattenValues(userRewardOut.fullTxOutValue).find(_._1 == deposit.coinLq.value))
      poolIn   <- OptionT.fromOption((tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption))
      poolOut <- OptionT.fromOption(tx.txOutputs.find { txOut =>
                   val fValues = flattenValues(txOut.fullTxOutValue)
                   fValues.contains(deposit.poolId.value)
                 })
    } yield DepositOrderInfo(
      amountLq._2,
      userRewardOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ userRewardOut.fullTxOutRef.txOutRefIdx.toString,
      poolIn.txInRef.txOutRefId.getTxId ++ "#" ++ poolIn.txInRef.txOutRefIdx.toString,
      poolOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ poolOut.fullTxOutRef.txOutRefIdx.toString,
      tx.slotNo,
      input.txInRef.txOutRefId.getTxId ++ "#" ++ input.txInRef.txOutRefIdx.toString
    )).value

    private def resolveSwapOrder(swap: Swap, input: TxInput, tx: AppliedTransaction): F[Option[SwapOrderInfo]] = (for {
      userRewardOut <- OptionT.fromOption(tx.txOutputs.find { txOut =>
                         val fValues = flattenValues(txOut.fullTxOutValue)
                         fValues.contains(swap.quote.value) && checkForPubkey(
                           swap.rewardPkh,
                           txOut.fullTxOutAddress.addressCredential
                         )
                       })
      actualQuote <- OptionT.fromOption(flattenValues(userRewardOut.fullTxOutValue).find(_._1 == swap.quote.value))
      poolIn      <- OptionT.fromOption(tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption)
      poolOut <- OptionT.fromOption(tx.txOutputs.find { txOut =>
                   val fValues = flattenValues(txOut.fullTxOutValue)
                   fValues.contains(swap.poolId.value)
                 })
    } yield SwapOrderInfo(
      actualQuote._2,
      userRewardOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ userRewardOut.fullTxOutRef.txOutRefIdx.toString,
      poolIn.txInRef.txOutRefId.getTxId ++ "#" ++ poolIn.txInRef.txOutRefIdx.toString,
      poolOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ poolOut.fullTxOutRef.txOutRefIdx.toString,
      tx.slotNo,
      input.txInRef.txOutRefId.getTxId ++ "#" ++ input.txInRef.txOutRefIdx.toString
    )).value

    private def resolveRedeemOrder(redeem: Redeem, input: TxInput, tx: AppliedTransaction): Option[RedeemOrderInfo] =
      for {
        userRewardOut <- tx.txOutputs.find { txOut =>
                           val fValues = flattenValues(txOut.fullTxOutValue)
                           fValues.contains(redeem.coinX.value) && fValues.contains(
                             redeem.coinY.value
                           ) && checkForPubkey(
                             redeem.rewardPkh.getPubKeyHash,
                             txOut.fullTxOutAddress.addressCredential
                           )
                         }
        actualX <- flattenValues(userRewardOut.fullTxOutValue).find(_._1 == redeem.coinX.value)
        actualY <- flattenValues(userRewardOut.fullTxOutValue).find(_._1 == redeem.coinY.value)
        poolIn  <- tx.txInputs.filterNot(_.txInRef == input.txInRef).headOption
        poolOut <- tx.txOutputs.find { txOut =>
                     val fValues = flattenValues(txOut.fullTxOutValue)
                     fValues.contains(redeem.poolId.value)
                   }
      } yield RedeemOrderInfo(
        actualX._2,
        actualY._2,
        userRewardOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ userRewardOut.fullTxOutRef.txOutRefIdx.toString,
        poolIn.txInRef.txOutRefId.getTxId ++ "#" ++ poolIn.txInRef.txOutRefIdx.toString,
        poolOut.fullTxOutRef.txOutRefId.getTxId ++ "#" ++ poolOut.fullTxOutRef.txOutRefIdx.toString,
        tx.slotNo,
        input.txInRef.txOutRefId.getTxId ++ "#" ++ input.txInRef.txOutRefIdx.toString
      )

    override def handle(in: NonEmptyList[TxEvent]): F[Unit] =
      in.traverse {
        case _: UnAppliedTransaction => ().pure[F]
        case tx: AppliedTransaction =>
          tx.txInputs.traverse { txInput =>
            ordersRepository.getOrder(
              txInput.txInRef.txOutRefId.getTxId ++ "#" ++ txInput.txInRef.txOutRefIdx.toString
            ) flatMap {
              case Some(deposit: Deposit) =>
                info"Deposit order in tx: ${tx.toString}" >> (resolveDepositOrder(deposit, txInput, tx) flatMap {
                  case Some(
                        DepositOrderInfo(amountLq, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId)
                      ) =>
                    ordersRepository
                      .updateExecutedDepositOrder(
                        amountLq,
                        userOutputId,
                        poolInputId,
                        poolOutputId,
                        timestamp,
                        orderInputId
                      )
                      .void
                  case None => ().pure[F]
                })
              case Some(swap: Swap) =>
                info"Swap order in tx: ${tx.toString}" >> (resolveSwapOrder(swap, txInput, tx) flatMap {
                  case Some(
                        SwapOrderInfo(actualQuote, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId)
                      ) =>
                    ordersRepository
                      .updateExecutedSwapOrder(
                        actualQuote,
                        userOutputId,
                        poolInputId,
                        poolOutputId,
                        timestamp,
                        orderInputId
                      )
                      .void
                  case None => ().pure[F]
                })
              case Some(redeem: Redeem) =>
                info"Redeem order in tx: ${tx.toString}" >> (resolveRedeemOrder(redeem, txInput, tx) match {
                  case Some(
                        RedeemOrderInfo(
                          amountX,
                          amountY,
                          userOutputId,
                          poolInputId,
                          poolOutputId,
                          timestamp,
                          orderInputId
                        )
                      ) =>
                    ordersRepository
                      .updateExecutedRedeemOrder(
                        amountX,
                        amountY,
                        userOutputId,
                        poolInputId,
                        poolOutputId,
                        timestamp,
                        orderInputId
                      )
                      .void
                  case None => ().pure[F]
                })
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
