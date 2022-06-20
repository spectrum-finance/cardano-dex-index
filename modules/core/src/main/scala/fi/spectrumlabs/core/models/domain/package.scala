package fi.spectrumlabs.core.models

import io.estatico.newtype.macros.newtype
import tofu.logging.Loggable

package object domain {
  @newtype final case class PoolId(value: String)

  object PoolId {
    implicit val loggable: Loggable[PoolId] = deriving
  }

  @newtype final case class Amount(value: Long)

  object Amount {
    implicit val loggable: Loggable[Amount] = deriving
  }
}
