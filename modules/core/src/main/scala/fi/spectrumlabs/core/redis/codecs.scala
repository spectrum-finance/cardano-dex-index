package fi.spectrumlabs.core.redis

import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.data.RedisCodec

import scala.util.Try

object codecs {

  val longCodec: RedisCodec[String, Long] = Codecs.derive(RedisCodec.Utf8, stringLongEpi)

  val stringCodec: RedisCodec[String, String] = RedisCodec.Utf8

  private def stringLongEpi: SplitEpi[String, Long] = SplitEpi(s => Try(s.toLong).getOrElse(0), _.toString)
}
