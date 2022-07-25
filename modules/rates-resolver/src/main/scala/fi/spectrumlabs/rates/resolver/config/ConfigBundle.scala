package fi.spectrumlabs.rates.resolver.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import fi.spectrumlabs.core.config.ConfigBundleCompanion
import fi.spectrumlabs.core.pg.PgConfig
import fi.spectrumlabs.core.redis.RedisConfig
import tofu.WithContext
import tofu.logging.derivation.loggable
import tofu.optics.macros.ClassyOptics

@ClassyOptics
@derive(loggable, pureconfigReader)
final case class ConfigBundle(
  resolver: ResolverConfig,
  redis: RedisConfig,
  pg: PgConfig,
  network: NetworkConfig,
  tokens: TokenFetcherConfig
)

object ConfigBundle extends WithContext.Companion[ConfigBundle] with ConfigBundleCompanion[ConfigBundle]
