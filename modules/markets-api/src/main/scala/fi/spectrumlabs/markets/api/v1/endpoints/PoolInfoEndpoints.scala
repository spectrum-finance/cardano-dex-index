package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.models.domain.PoolId
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.models.{PoolInfo, PoolOverview}
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import fi.spectrumlabs.markets.api.v1.endpoints._

import scala.concurrent.duration.FiniteDuration

object PoolInfoEndpoints {

  val pathPrefix = "pool"

  def endpoints: List[Endpoint[_, _, _, _]] = getPoolInfo :: getPoolsOverview :: Nil

  def getPoolInfo: Endpoint[(PoolId, FiniteDuration), HttpError, PoolInfo, Any] =
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
      .out(jsonBody[PoolInfo])
      .tag(pathPrefix)
      .name("Info by pool id")
      .description("Allow to get info about pool within period")

  def getPoolsOverview: Endpoint[FiniteDuration, HttpError, List[PoolOverview], Any] =
    baseEndpoint.get
      .in("pools" / "overview")
      .in(after)
      .out(jsonBody[List[PoolOverview]])
      .tag(pathPrefix)
      .name("Pools overview")
      .description("Allow to get info about all pool within period")
}
