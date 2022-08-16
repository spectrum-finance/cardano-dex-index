package fi.spectrumlabs.markets.api.configs

import derevo.derive
import derevo.pureconfig.pureconfigReader
import fi.spectrumlabs.core.config.ConfigBundleCompanion
import fi.spectrumlabs.core.pg.PgConfig
import fi.spectrumlabs.core.redis.RedisConfig
import fi.spectrumlabs.rates.resolver.config.TokenFetcherConfig
import tofu.WithContext
import tofu.logging.derivation.loggable
import tofu.optics.macros.ClassyOptics

@ClassyOptics
@derive(loggable, pureconfigReader)
final case class ConfigBundle(
  marketsApi: MarketsApiConfig,
  redis: RedisConfig,
  pg: PgConfig,
  http: HttpConfig,
  tokenFetcher: TokenFetcherConfig
)

object ConfigBundle extends WithContext.Companion[ConfigBundle] with ConfigBundleCompanion[ConfigBundle]
