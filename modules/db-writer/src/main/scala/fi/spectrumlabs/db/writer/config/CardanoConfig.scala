package fi.spectrumlabs.db.writer.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.logging.derivation.loggable

@derive(loggable, pureconfigReader)
final case class CardanoConfig(
  startTimeInSeconds: Long,
  //pools id token
  supportedPools: List[String]
)
