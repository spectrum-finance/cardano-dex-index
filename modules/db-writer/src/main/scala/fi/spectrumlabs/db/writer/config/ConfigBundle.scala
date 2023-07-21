package fi.spectrumlabs.db.writer.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import fi.spectrumlabs.core.config.ConfigBundleCompanion
import fi.spectrumlabs.core.streaming.config.{ConsumerConfig, KafkaConfig}
import tofu.WithContext
import tofu.logging.derivation.loggable
import tofu.optics.macros.ClassyOptics
import fi.spectrumlabs.core.pg.PgConfig
import fi.spectrumlabs.core.redis.RedisConfig

@ClassyOptics
@derive(loggable, pureconfigReader)
final case class ConfigBundle(
  pg: PgConfig,
  txConsumer: ConsumerConfig,
  executedOpsConsumer: ConsumerConfig,
  mempoolOpsConsumer: ConsumerConfig,
  poolsConsumer: ConsumerConfig,
  redisApiCache: RedisConfig,
  kafka: KafkaConfig,
  writer: WriterConfig,
  cardanoConfig: CardanoConfig
)

object ConfigBundle extends WithContext.Companion[ConfigBundle] with ConfigBundleCompanion[ConfigBundle]
