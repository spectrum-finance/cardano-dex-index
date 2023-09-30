package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.models.PoolOverviewFront
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, PendingNeedRefundResponse}
import sttp.tapir.Endpoint
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

object FrontApiEndpoints {

  val pathPrefix = "front"

  def endpoints: List[Endpoint[_, _, _, _]] = poolsApiE :: Nil

  def poolsApiE: Endpoint[Unit, HttpError, List[PoolOverviewFront], Any] =
    baseEndpoint.get
      .in("front" / "pools")
      .out(jsonBody[List[PoolOverviewFront]])
      .tag(pathPrefix)
      .name("Pools overview")
      .description("Allow to get info about all pool within period")
}
