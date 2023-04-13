package fi.spectrumlabs.core

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.{Get, Put}
import io.estatico.newtype.macros.newtype
import tofu.logging.derivation.loggable

package object models {

  @derive(loggable, encoder, decoder)
  @newtype case class TxHash(value: String)

  object TxHash {
    implicit val get: Get[TxHash] = deriving
    implicit val put: Put[TxHash] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class BlockHash(value: String)

  object BlockHash {
    implicit val get: Get[BlockHash] = deriving
    implicit val put: Put[BlockHash] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class OutRef(value: String)

  object OutRef {
    implicit val get: Get[OutRef] = deriving
    implicit val put: Put[OutRef] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class Bytea(value: String)

  object Bytea {
    implicit val get: Get[Bytea] = deriving
    implicit val put: Put[Bytea] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class PolicyId(value: String)

  object PolicyId {
    implicit val get: Get[PolicyId] = deriving
    implicit val put: Put[PolicyId] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class Asset32(value: String)

  object Asset32 {
    implicit val get: Get[Asset32] = deriving
    implicit val put: Put[Asset32] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class Addr(value: String)

  object Addr {
    implicit val get: Get[Addr] = deriving
    implicit val put: Put[Addr] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class PaymentCred(value: String)

  object PaymentCred {
    implicit val get: Get[PaymentCred] = deriving
    implicit val put: Put[PaymentCred] = deriving
  }

  @derive(loggable, encoder, decoder)
  @newtype case class Hash32(value: String)

  object Hash32 {
    implicit val get: Get[Hash32] = deriving
    implicit val put: Put[Hash32] = deriving
  }
}
