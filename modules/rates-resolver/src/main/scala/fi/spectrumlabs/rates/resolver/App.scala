package fi.spectrumlabs.rates.resolver

import cats.effect.{Blocker, Resource}
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.pg.{doobieLogging, PostgresTransactor}
import fi.spectrumlabs.core.network._
import fi.spectrumlabs.core.redis._
import fi.spectrumlabs.core.redis.codecs._
import fi.spectrumlabs.rates.resolver.config.{AppContext, ConfigBundle}
import fi.spectrumlabs.rates.resolver.gateways.NetworkClient
import fi.spectrumlabs.rates.resolver.repositories.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.rates.resolver.services.{PoolsService, RatesResolver}
import sttp.client3.SttpBackend
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.fs2Instances._
import tofu.lift.{IsoK, Unlift}
import tofu.logging.Logs
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
      implicit0(ul: Unlift[RunF, InitF])    = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      trans <- PostgresTransactor.make[InitF]("meta-db-pool", configs.pgConfig)
      implicit0(xa: Txr.Continuational[RunF]) = Txr.continuational[RunF](trans.mapK(wr.liftF))
      implicit0(elh: EmbeddableLogHandler[xa.DB]) <- Resource.eval(
                                                      doobieLogging.makeEmbeddableHandler[InitF, RunF, xa.DB](
                                                        "db-pools-resolver-logging"
                                                      )
                                                    )
      implicit0(sttp: SttpBackend[RunF, Any]) <- makeBackend[AppContext, InitF, RunF](ctx, blocker)
      implicit0(logsDb: Logs[InitF, xa.DB]) = Logs.sync[InitF, xa.DB]

      implicit0(redis: RedisCommands[RunF, String, String]) <- mkRedis[String, String, InitF, RunF](
                                                                configs.redisConfig,
                                                                stringCodec
                                                              )

      poolsRepo <- Resource.eval(PoolsRepo.create[InitF, xa.DB, RunF])
      poolsService = PoolsService.create[RunF](poolsRepo)
      ratesRepo    = RatesRepo.create[RunF]
      network <- Resource.eval(NetworkClient.create[InitF, RunF])
      resolver <- Resource.eval(
                   RatesResolver.create[InitF, StreamF, RunF](poolsService, ratesRepo, network, configs.resolverConfig)
                 )
      _ <- Resource.eval(resolver.run.compile.drain).mapK(isoKRun.tof)
    } yield ()
}
