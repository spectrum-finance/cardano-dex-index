package fi.spectrumlabs.markets.api.configs

import derevo.derive
import derevo.pureconfig.pureconfigReader
import sttp.model.Uri
import tofu.WithContext
import tofu.logging.derivation.loggable
import fi.spectrumlabs.core.network._

@derive(pureconfigReader, loggable)
final case class NetworkConfig(
  cmcUrl: Uri,
  cmcApiKey: String
)

object NetworkConfig extends WithContext.Companion[NetworkConfig]
