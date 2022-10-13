package fi.spectrumlabs

import cats.effect.{Blocker, Resource}
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs
import fi.spectrumlabs.config.{AppContext, ConfigBundle}
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.models.{Block, Tx}
import fi.spectrumlabs.core.network._
import fi.spectrumlabs.core.redis._
import fi.spectrumlabs.core.redis.codecs._
import fi.spectrumlabs.core.streaming.Producer
import fi.spectrumlabs.core.streaming.serde._
import fi.spectrumlabs.programs.{BlockTrackerProgram, TxTrackerProgram}
import fi.spectrumlabs.repositories.TrackerCache
import fi.spectrumlabs.services.Explorer
import fs2.Chunk
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import tofu.fs2Instances._
import tofu.lift.{IsoK, Unlift}
import tofu.logging.derivation.loggable.generate
import zio.interop.catz._
import zio.{ExitCode, URIO, ZIO}

object App extends EnvApp[AppContext] {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    init(args.headOption).use(_ => ZIO.never).orDie

  def init(configPathOpt: Option[String]): Resource[InitF, Unit] =
    for {
      blocker <- Blocker[InitF]
      configs <- Resource.eval(ConfigBundle.load[InitF](configPathOpt, blocker))
      ctx                                   = AppContext.init(configs)
      implicit0(isoKRun: IsoK[RunF, InitF]) = isoKRunByContext(ctx)
      txProducer: Producer[String, Tx, StreamF] <- Producer.make[InitF, StreamF, RunF, String, Tx](
                                                   configs.producer,
                                                   configs.kafka
                                                 )
      blockProducer: Producer[String, Block, StreamF] <- Producer.make[InitF, StreamF, RunF, String, Block](
                                                   configs.producer,
                                                   configs.kafka
                                                 )
      implicit0(ul: Unlift[RunF, InitF]) = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      implicit0(redis: RedisCommands[RunF, String, Long]) <- mkRedis[String, Long, InitF, RunF](
                                                               configs.redis,
                                                               longCodec
                                                             )
      implicit0(backend: SttpBackend[RunF, Fs2Streams[RunF]]) <- makeBackend[AppContext, InitF, RunF](ctx, blocker)
      implicit0(cache: TrackerCache[RunF])                    <- Resource.eval(TrackerCache.create[InitF, RunF](configs.redis))
      implicit0(explorer: Explorer[StreamF, RunF]) <- Resource.eval(
                                                        Explorer.create[StreamF, RunF, InitF](configs.explorer)
                                                      )
      implicit0(txTracker: TxTrackerProgram[StreamF]) <- Resource.eval(
                                                       TxTrackerProgram.create[StreamF, RunF, InitF, Chunk](
                                                         txProducer,
                                                         configs.txTracker
                                                       )
                                                     )
      implicit0(blockTracker: BlockTrackerProgram[StreamF]) <- Resource.eval(
                                                       BlockTrackerProgram.create[StreamF, RunF, InitF, Chunk](
                                                         blockProducer,
                                                         configs.blockTracker
                                                       )
                                                     )
      _ <- Resource.eval(fs2.Stream(txTracker.run, blockTracker.run).parJoinUnbounded.compile.drain).mapK(ul.liftF)
    } yield ()
}
