package fi.spectrumlabs

import cats.effect.{Blocker, Resource, Sync}
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fi.spectrumlabs.config.{AppContext, ConfigBundle}
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.models.Tx
import fi.spectrumlabs.core.streaming.Producer
import fi.spectrumlabs.programs.TrackerProgram
import fi.spectrumlabs.repositories.TrackerCache
import fi.spectrumlabs.services.Explorer
import fs2.Chunk
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import tofu.WithRun
import tofu.fs2Instances._
import tofu.lift.{IsoK, Unlift}
import tofu.logging.derivation.loggable.generate
import tofu.syntax.unlift._
import zio.interop.catz._
import zio.{ExitCode, URIO, ZIO}
import fi.spectrumlabs.core.streaming.serde._

import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.Try

object App extends EnvApp[AppContext] {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    init(args.headOption).use(_ => ZIO.never).orDie

  def init(configPathOpt: Option[String]): Resource[InitF, Unit] =
    for {
      blocker <- Blocker[InitF]
      configs <- Resource.eval(ConfigBundle.load[InitF](configPathOpt, blocker))
      ctx                                   = AppContext.init(configs)
      implicit0(isoKRun: IsoK[RunF, InitF]) = isoKRunByContext(ctx)
      producer: Producer[String, Tx, StreamF] <- Producer.make[InitF, StreamF, RunF, String, Tx](
                                                  configs.producer,
                                                  configs.kafka
                                                )
      implicit0(ul: Unlift[RunF, InitF]) = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      implicit0(redis: RedisCommands[RunF, String, Long])     <- mkRedis(ctx)
      implicit0(backend: SttpBackend[RunF, Fs2Streams[RunF]]) <- makeBackend(ctx, blocker)
      implicit0(cache: TrackerCache[RunF])                    <- Resource.eval(TrackerCache.create[InitF, RunF](configs.redis))
      implicit0(explorer: Explorer[StreamF, RunF]) <- Resource.eval(
                                                       Explorer.create[StreamF, RunF, InitF](configs.explorer)
                                                     )
      implicit0(tracker: TrackerProgram[StreamF]) <- Resource.eval(
                                                      TrackerProgram.create[StreamF, RunF, InitF, Chunk](
                                                        producer,
                                                        configs.tracker
                                                      )
                                                    )
      _ <- Resource.eval(tracker.run.compile.drain).mapK(ul.liftF)
    } yield ()

  private def mkRedis(
    ctx: AppContext
  )(implicit ul: Unlift[RunF, InitF]): Resource[InitF, RedisCommands[RunF, String, Long]] = {

    import ctx.config.redis._
    import dev.profunktor.redis4cats.effect.Log.Stdout._
    def stringLongEpi: SplitEpi[String, Long] = SplitEpi(s => Try(s.toLong).getOrElse(0), _.toString)
    val longCodec                             = Codecs.derive(RedisCodec.Utf8, stringLongEpi)
    for {
      timeoutOptions <- Resource.eval(Sync[InitF].delay(TimeoutOptions.builder().fixedTimeout(timeout.toJava).build()))
      clientOptions  <- Resource.eval(Sync[InitF].delay(ClientOptions.builder().timeoutOptions(timeoutOptions).build()))
      client         <- RedisClient[RunF].withOptions(s"redis://$password@$host:$port", clientOptions).mapK(ul.liftF)
      redisCmd       <- Redis[RunF].fromClient(client, longCodec).mapK(ul.liftF)
    } yield redisCmd
  }

  private def makeBackend(
    ctx: AppContext,
    blocker: Blocker
  )(implicit wr: WithRun[RunF, InitF, AppContext]): Resource[InitF, SttpBackend[RunF, Fs2Streams[RunF]]] =
    Resource
      .eval(wr.concurrentEffect)
      .flatMap(implicit ce => AsyncHttpClientFs2Backend.resource[RunF](blocker))
      .mapK(wr.runContextK(ctx))
}
