package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.Put
import doobie.util.Get
import cats.syntax.either._
import fi.spectrumlabs.db.writer.models.orders.{TxOutRef, TxOutRefId => OTxOutRefId}
import tofu.logging.derivation.loggable

import scala.util.{Failure, Success, Try}

@derive(encoder, decoder, loggable)
final case class FullTxOutRef(txOutRefId: TxOutRefId, txOutRefIdx: Int)

object FullTxOutRef {

  def fromTxOutRef(txOutRef: TxOutRef): FullTxOutRef =
    FullTxOutRef(TxOutRefId(txOutRef.txOutRefId.getTxId), txOutRef.txOutRefIdx)

  def toTxOutRef(ref: FullTxOutRef): TxOutRef =
    TxOutRef(ref.txOutRefIdx, OTxOutRefId(ref.txOutRefId.getTxId))

  def fromString(outRef: String): Either[String, FullTxOutRef] =
    outRef.split(CardanoRefDelimiter).toList match {
      case ref :: idxStr :: Nil =>
        Try(idxStr.toInt) match {
          case Failure(_) =>
            (s"Couldn't parse $ref$CardanoRefDelimiter$idxStr to FullTxOutRef from db. " +
            s"Problem with parsing $idxStr to Int").asLeft
          case Success(idx) => FullTxOutRef(TxOutRefId(ref), idx).asRight
        }
      case unknownString => s"Couldn't parse $unknownString to FullTxOutRef from db".asLeft
    }

  implicit val put: Put[FullTxOutRef] =
    Put[String].contramap(ref => ref.txOutRefId.getTxId ++ CardanoRefDelimiter ++ ref.txOutRefIdx.toString)

  implicit val get: Get[FullTxOutRef] =
    Get[String].temap(fromString)
}

@derive(encoder, decoder, loggable)
final case class TxOutRefId(getTxId: String)
