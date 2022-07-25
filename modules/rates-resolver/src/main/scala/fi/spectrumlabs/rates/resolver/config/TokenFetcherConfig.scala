package fi.spectrumlabs.rates.resolver.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.Context
import tofu.logging.derivation.loggable

@derive(pureconfigReader, loggable)
case class TokenFetcherConfig(url: String)

object TokenFetcherConfig extends Context.Companion[TokenFetcherConfig]
