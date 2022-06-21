package fi.spectrumlabs.rates.resolver

import cats.effect.{Blocker, Resource, Sync}
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.rates.resolver.config.{AppContext, ConfigBundle}
import fi.spectrumlabs.rates.resolver.gateways.NetworkClient
import fi.spectrumlabs.rates.resolver.repositories.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.rates.resolver.services.{PoolsService, RatesResolver}
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import tofu.WithRun
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.lift.{IsoK, Unlift}
import tofu.logging.Logs
import tofu.syntax.unlift.UnliftEffectOps
import zio.{ExitCode, URIO, ZIO}
import zio.interop.catz._
import tofu.fs2Instances._
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
      implicit0(ul: Unlift[RunF, InitF])    = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      trans <- PostgresTransactor.make[InitF]("meta-db-pool", configs.pgConfig)
      implicit0(xa: Txr.Continuational[RunF]) = Txr.continuational[RunF](trans.mapK(wr.liftF))
      implicit0(elh: EmbeddableLogHandler[xa.DB]) <- Resource.eval(
                                                       doobieLogging.makeEmbeddableHandler[InitF, RunF, xa.DB](
                                                         "db-pools-resolver-logging"
                                                       )
                                                     )
      implicit0(sttp: SttpBackend[RunF, Any]) <- makeBackend(ctx, blocker)
      implicit0(logsDb: Logs[InitF, xa.DB]) = Logs.sync[InitF, xa.DB]
      redis     <- mkRedis(ctx)
      poolsRepo <- Resource.eval(PoolsRepo.create[InitF, xa.DB, RunF])
      poolsService = PoolsService.create[RunF](poolsRepo)
      ratesRepo    = RatesRepo.create[RunF](redis)
      network <- Resource.eval(NetworkClient.create[InitF, RunF])
      resolver <- Resource.eval(
                    RatesResolver.create[InitF, StreamF, RunF](poolsService, ratesRepo, network, configs.resolverConfig)
                  )
      _ <- Resource.eval(resolver.run.compile.drain).mapK(isoKRun.tof)
    } yield ()

  private def mkRedis(
    ctx: AppContext
  )(implicit ul: Unlift[RunF, InitF]): Resource[InitF, RedisCommands[RunF, String, String]] = {

    import ctx.config.redisConfig._
    import dev.profunktor.redis4cats.effect.Log.Stdout._
    for {
      timeoutOptions <- Resource.eval(Sync[InitF].delay(TimeoutOptions.builder().fixedTimeout(timeout.toJava).build()))
      clientOptions  <- Resource.eval(Sync[InitF].delay(ClientOptions.builder().timeoutOptions(timeoutOptions).build()))
      client         <- RedisClient[RunF].withOptions(s"redis://$password@$host:$port", clientOptions).mapK(ul.liftF)
      redisCmd       <- Redis[RunF].fromClient(client, RedisCodec.Utf8).mapK(ul.liftF)
    } yield redisCmd
  }

  private def makeBackend(
    ctx: AppContext,
    blocker: Blocker
  )(implicit wr: WithRun[RunF, InitF, AppContext]): Resource[InitF, SttpBackend[RunF, Any]] =
    Resource
      .eval(wr.concurrentEffect)
      .flatMap(implicit ce => AsyncHttpClientFs2Backend.resource[RunF](blocker))
      .mapK(wr.runContextK(ctx))
}
