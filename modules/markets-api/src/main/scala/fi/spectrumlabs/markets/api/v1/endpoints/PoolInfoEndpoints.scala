package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.models.domain.PoolId
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.models.{PlatformStats, PoolList, PoolOverview, PoolOverviewNew, PoolState, PricePoint}
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow

import scala.concurrent.duration.FiniteDuration

object PoolInfoEndpoints {

  val pathPrefix = "pool"

  def endpoints: List[Endpoint[_, _, _, _]] =
    getPoolStateByDate :: getPoolList :: getPoolInfo :: getPoolsOverview :: Nil

  def getPoolInfo: Endpoint[(PoolId, FiniteDuration), HttpError, PoolOverview, Any] =
    baseEndpoint.get
      .in(pathPrefix / "info")
      .in(
        path[PoolId]
          .description(
            "Pool id (Concatenation of base16encoded CurrencySymbol and base16encoded TokenName with 'dot' delimiter.)"
          )
          .name("poolId")
          .example(PoolId("93a4e3ab42b052cbe48bee3a6507d3ec06b9555994c1e6815f296108.484f534b59745f414441745f6e6674"))
      )
      .in(after)
      .out(jsonBody[PoolOverview])
      .tag(pathPrefix)
      .name("Info by pool id")
      .description("Allow to get info about pool within period")

  def getPoolsOverview: Endpoint[Unit, HttpError, List[PoolOverview], Any] =
    baseEndpoint.get
      .in("pools" / "overview")
      .out(jsonBody[List[PoolOverview]])
      .tag(pathPrefix)
      .name("Pools overview")
      .description("Allow to get info about all pool within period")

  def getPoolPriceChart: Endpoint[(PoolId, TimeWindow, Long), HttpError, List[PricePoint], Any] =
    baseEndpoint.get
      .in("pool" / path[PoolId] / "chart")
      .in(timeWindow)
      .in(minutesResolution)
      .out(jsonBody[List[PricePoint]])
      .tag(pathPrefix)
      .name("Pool price chart")
      .description("Allow to get pool price chart within period")

  def getPlatformStats: Endpoint[Unit, HttpError, PlatformStats, Any] =
    baseEndpoint.get
      .in("platform" / "stats")
      .out(jsonBody[PlatformStats])
      .tag(pathPrefix)
      .name("Platform summary")
      .description("Allow to get platform summary within period")

  def getPoolList: Endpoint[Unit, HttpError, PoolList, Any] =
    baseEndpoint.get
      .in("pools" / "list")
      .out(jsonBody[PoolList])
      .tag(pathPrefix)
      .name("Pool list")
      .description("Allow to get ids and number of existing pools")

  def getPoolStateByDate: Endpoint[(PoolId, Long), HttpError, PoolState, Any] =
    baseEndpoint.get
      .in(pathPrefix / path[PoolId] / "state")
      .in(query[Long]("date"))
      .out(jsonBody[PoolState])
      .tag(pathPrefix)
      .name("Pool state by id and date")
      .description("Allow to get pool state at given date")
}
