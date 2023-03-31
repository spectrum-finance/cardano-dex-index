package fi.spectrumlabs.core.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Json

@derive(encoder, decoder)
final case class Metadata(key: BigInt, raw: Bytea, json: Json)

