package fi.spectrumlabs.markets.api.configs

import derevo.derive
import derevo.pureconfig.pureconfigReader
import fi.spectrumlabs.rates.resolver.config.TokenFetcherConfig
import sttp.model.Uri
import tofu.logging.derivation.loggable
import fi.spectrumlabs.core.network._

@derive(loggable, pureconfigReader)
final case class TokenFetcherConfig1(uri: Uri)