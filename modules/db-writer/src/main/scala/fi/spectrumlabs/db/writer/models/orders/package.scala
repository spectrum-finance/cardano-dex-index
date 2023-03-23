package fi.spectrumlabs.db.writer.models

import cats.Show
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.{Get, Put, Read}
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import tofu.logging.Loggable
import tofu.logging.derivation.{loggable, show}

package object orders {

  @derive(decoder, encoder, loggable, show)
  final case class ExFee(unExFee: Long)

  @derive(decoder, encoder, loggable, show)
  final case class PublicKeyHash(getPubKeyHash: String)

  @newtype final case class CollateralAda(value: Long)

  object CollateralAda {
    implicit val encoder: Encoder[CollateralAda]   = deriving
    implicit val decoder: Decoder[CollateralAda]   = deriving
    implicit val loggable: Loggable[CollateralAda] = deriving
    implicit val get: Get[CollateralAda]           = deriving
    implicit val put: Put[CollateralAda]           = deriving
    implicit val show: Show[CollateralAda]         = deriving
  }

  @derive(decoder, encoder, loggable, show)
  final case class TxOutRef(txOutRefIdx: Int, txOutRefId: TxOutRefId)

  //todo: refactor me
  object TxOutRef {
    implicit val put: Put[TxOutRef] = Put[String].contramap(r => s"${r.txOutRefId.getTxId}#${r.txOutRefIdx}")
    implicit val get: Get[TxOutRef] = Get[String].map(r => r.split("#").toList match {
      case ref :: id :: Nil => TxOutRef(id.toInt, TxOutRefId(ref))
      case _                => throw new Exception(s"Err in reading txOutref from db. $r")
    })
    implicit val read: Read[TxOutRef] = Read[String].map(r => r.split("#").toList match {
      case ref :: id :: Nil => TxOutRef(id.toInt, TxOutRefId(ref))
      case _                => throw new Exception(s"Err in reading txOutref from db. $r")
    })
  }

  @derive(decoder, encoder, loggable, show)
  final case class TxOutRefId(getTxId: String)

  @derive(decoder, encoder, loggable, show)
  final case class StakePKH(unStakePubKeyHash: StakePubKeyHash)

  object StakePKH {

    implicit val get: Get[StakePKH] = Get[String].map(sph => StakePKH(StakePubKeyHash(sph)))
    implicit val read: Read[StakePKH] = Read[String].map(sph => StakePKH(StakePubKeyHash(sph)))
  }

  @derive(decoder, encoder, loggable, show)
  final case class StakePubKeyHash(getPubKeyHash: String)

}
