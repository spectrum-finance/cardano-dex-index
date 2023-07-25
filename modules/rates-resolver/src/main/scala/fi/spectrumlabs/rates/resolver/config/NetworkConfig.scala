package fi.spectrumlabs.rates.resolver.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import sttp.model.Uri
import tofu.logging.derivation.loggable
import fi.spectrumlabs.core.network._

import scala.concurrent.duration.FiniteDuration

@derive(pureconfigReader, loggable)
final case class NetworkConfig(cmcUrl: Uri, cmcApiKey: String, tokensUrl: Uri, tokensTtl: FiniteDuration)
