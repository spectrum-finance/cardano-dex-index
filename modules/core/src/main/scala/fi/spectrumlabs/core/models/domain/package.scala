package fi.spectrumlabs.core.models

import io.estatico.newtype.macros.newtype

package object domain {
  @newtype final case class PoolId(value: String)
  @newtype final case class Amount(value: Long)
}
