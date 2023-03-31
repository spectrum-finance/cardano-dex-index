package fi.spectrumlabs.core.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.{Metadata => M}
import io.circe.Json
import io.scalaland.chimney.dsl._

@derive(encoder, decoder)
final case class Meta(key: BigInt, raw: Bytea, json: Json)

object Meta {

  def fromExplorer(meta: M): Meta =
    meta.into[Meta].transform
}
