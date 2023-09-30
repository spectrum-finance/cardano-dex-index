package fi.spectrumlabs.markets.api.configs

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.WithContext
import tofu.logging.derivation.loggable

@derive(pureconfigReader, loggable)
final case class GraphiteSettings(
  host: String,
  port: Int,
  prefix: String
)

object GraphiteSettings extends WithContext.Companion[GraphiteSettings]
