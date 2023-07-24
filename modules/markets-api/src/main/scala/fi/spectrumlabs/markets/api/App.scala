package fi.spectrumlabs.markets.api

import cats.effect.{Blocker, Resource}
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.data.RedisCodec
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.cache.Cache
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.core.http.cache.CacheMiddleware.CachingMiddleware
import fi.spectrumlabs.core.http.cache.{CacheMiddleware, HttpResponseCaching}
import fi.spectrumlabs.core.network.makeBackend
import fi.spectrumlabs.core.pg.{PostgresTransactor, doobieLogging}
import fi.spectrumlabs.core.redis.codecs.stringCodec
import fi.spectrumlabs.core.redis.mkRedis
import fi.spectrumlabs.db.writer.repositories.OrdersRepository
import fi.spectrumlabs.markets.api.configs.ConfigBundle
import fi.spectrumlabs.markets.api.context.AppContext
import fi.spectrumlabs.markets.api.repositories.repos.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.markets.api.services.{AmmStatsMath, AnalyticsService, CacheCleaner, HistoryService, MempoolService, TokenFetcher1}
import fi.spectrumlabs.markets.api.v1.HttpServer
import fi.spectrumlabs.rates.resolver.gateways.Metadata
import fi.spectrumlabs.rates.resolver.services.MetadataService
import fi.spectrumlabs.rates.resolver.services.{TokenFetcher => TF}
import org.http4s.server.Server
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.tapir.server.http4s.Http4sServerOptions
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.lift.{IsoK, Unlift}
import tofu.logging.Logs
import tofu.logging.derivation.loggable.generate
import zio.interop.catz._
import zio.{ExitCode, URIO, ZIO}
import cats.tagless.syntax.functorK._

object App extends EnvApp[AppContext] {

  implicit val serverOptions: Http4sServerOptions[RunF, RunF] = Http4sServerOptions.default[RunF, RunF]

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    init(args.headOption).use(x => x._2.clean.as(ExitCode.success)).orDie

  def init(configPathOpt: Option[String]): Resource[InitF, (Server, CacheCleaner[InitF])] =
    for {
      blocker <- Blocker[InitF]
      configs <- Resource.eval(ConfigBundle.load[InitF](configPathOpt, blocker))
      ctx                                = AppContext.init(configs)
      implicit0(ul: Unlift[RunF, InitF]) = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      trans <- PostgresTransactor.make[InitF]("markets-api-db-pool", configs.pg)
      implicit0(xa: Txr.Continuational[RunF]) = Txr.continuational[RunF](trans.mapK(wr.liftF))
      implicit0(elh: EmbeddableLogHandler[xa.DB]) <- Resource.eval(
        doobieLogging.makeEmbeddableHandler[InitF, RunF, xa.DB](
          "markets-api-db-logging"
        )
      )
      implicit0(logsDb: Logs[InitF, xa.DB]) = Logs.sync[InitF, xa.DB]
      implicit0(redis: RedisCommands[RunF, String, String]) <- mkRedis[String, String, InitF, RunF](
        configs.ratesRedis,
        stringCodec
      )
      implicit0(plainRedis: Plain[RunF]) <- mkRedis[Array[Byte], Array[Byte], InitF, RunF](
        configs.httpRedis,
        RedisCodec.Bytes
      )
      implicit0(cache: Cache[RunF])                       <- Resource.eval(Cache.make[InitF, RunF])
      implicit0(httpRespCache: HttpResponseCaching[RunF]) <- Resource.eval(HttpResponseCaching.make[InitF, RunF])
      implicit0(httpCache: CachingMiddleware[RunF]) = CacheMiddleware.make[RunF]
      implicit0(backend: SttpBackend[RunF, Fs2Streams[RunF]]) <- makeBackend[AppContext, InitF, RunF](ctx, blocker)
      implicit0(tokens: TF[RunF]) = TF.make[RunF](configs.tokenFetcher)
      implicit0(metadata: Metadata[RunF])               <- Resource.eval(Metadata.create[InitF, RunF](configs.network))
      implicit0(tf: TokenFetcher1[RunF])               <-  Resource.eval(TokenFetcher1.create[InitF, RunF](configs.tf))
      implicit0(metadataService: MetadataService[RunF]) <- Resource.eval(MetadataService.create[InitF, RunF])
      implicit0(poolsRepo: PoolsRepo[RunF])             <- Resource.eval(PoolsRepo.create[InitF, xa.DB, RunF])
      ordersRepo                                        <- Resource.eval(OrdersRepository.make[InitF, RunF, xa.DB])
      implicit0(ratesRepo: RatesRepo[RunF])             <- Resource.eval(RatesRepo.create[InitF, RunF])
      implicit0(ammStatsMath: AmmStatsMath[RunF])       <- Resource.eval(AmmStatsMath.create[InitF, RunF])
      implicit0(mempoolService: MempoolService[RunF])   <- Resource.eval(MempoolService.make[InitF, RunF])
      implicit0(service: AnalyticsService[RunF]) <- Resource.eval(
        AnalyticsService.create[InitF, RunF](configs.marketsApi)
      )
      implicit0(historyService: HistoryService[RunF]) <- Resource.eval(HistoryService.make[InitF, RunF](ordersRepo))
      c = CacheCleaner.make[RunF](httpRespCache)
      server                                          <- HttpServer.make[InitF, RunF](configs.http, runtime.platform.executor.asEC)
    } yield (server, c.mapK(ul.liftF))
}
