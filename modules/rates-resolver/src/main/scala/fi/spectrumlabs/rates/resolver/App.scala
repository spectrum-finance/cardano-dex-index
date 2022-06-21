package fi.spectrumlabs.rates.resolver

import cats.effect.{Blocker, Resource}
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.pg.{doobieLogging, PostgresTransactor}
import fi.spectrumlabs.core.network._
import fi.spectrumlabs.core.redis._
import fi.spectrumlabs.core.redis.codecs._
import fi.spectrumlabs.rates.resolver.config.{AppContext, ConfigBundle}
import fi.spectrumlabs.rates.resolver.gateways.Network
import fi.spectrumlabs.rates.resolver.programs.Resolver
import fi.spectrumlabs.rates.resolver.repositories.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.rates.resolver.services.PoolsService
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
      trans <- PostgresTransactor.make[InitF]("rates-resolver-pool", configs.pg)
      implicit0(xa: Txr.Continuational[RunF]) = Txr.continuational[RunF](trans.mapK(wr.liftF))
      implicit0(elh: EmbeddableLogHandler[xa.DB]) <- Resource.eval(
                                                       doobieLogging.makeEmbeddableHandler[InitF, RunF, xa.DB](
                                                         "rates-resolver-db-logging"
                                                       )
                                                     )
      implicit0(sttp: SttpBackend[RunF, Any]) <- makeBackend[AppContext, InitF, RunF](ctx, blocker)
      implicit0(logsDb: Logs[InitF, xa.DB]) = Logs.sync[InitF, xa.DB]

      implicit0(redis: RedisCommands[RunF, String, String]) <- mkRedis[String, String, InitF, RunF](
                                                                 configs.redis,
                                                                 stringCodec
                                                               )
      implicit0(pRepo: PoolsRepo[RunF])       <- Resource.eval(PoolsRepo.create[InitF, xa.DB, RunF])
      implicit0(pService: PoolsService[RunF]) <- Resource.eval(PoolsService.create[InitF, RunF])
      implicit0(rates: RatesRepo[RunF])       <- Resource.eval(RatesRepo.create[InitF, RunF])
      implicit0(network: Network[RunF])       <- Resource.eval(Network.create[InitF, RunF](configs.network))
      resolver                                <- Resource.eval(Resolver.create[InitF, StreamF, RunF](configs.resolver))
      _                                       <- Resource.eval(resolver.run.compile.drain).mapK(isoKRun.tof)
    } yield ()
}
