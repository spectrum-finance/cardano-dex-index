package fi.spectrumlabs.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import fi.spectrumlabs.core.config.ConfigBundleCompanion
import fi.spectrumlabs.core.streaming.config.{KafkaConfig, ProducerConfig}
import tofu.WithContext
import tofu.logging.derivation.loggable
import tofu.optics.macros.{promote, ClassyOptics}
import fi.spectrumlabs.core.redis.RedisConfig

@ClassyOptics
@derive(loggable, pureconfigReader)
final case class ConfigBundle(
  explorer: ExplorerConfig,
  tracker: TrackerConfig,
  redis: RedisConfig,
  producer: ProducerConfig,
  @promote kafka: KafkaConfig
)

object ConfigBundle extends WithContext.Companion[ConfigBundle] with ConfigBundleCompanion[ConfigBundle]
