package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.models.domain.PoolId
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.models.PoolOverview
import fi.spectrumlabs.markets.api.v1.endpoints.PoolInfoEndpoints.pathPrefix
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, UserOrderInfo}
import sttp.tapir.{path, Endpoint}
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import scala.concurrent.duration.FiniteDuration

object HistoryEndpoints {

  val pathPrefix = "history"

  def endpoints: List[Endpoint[_, _, _, _]] = orderHistoryE :: Nil

  def orderHistoryE: Endpoint[(Paging, TimeWindow, HistoryApiQuery), HttpError, OrderHistoryResponse, Any] =
    baseEndpoint.post
      .in(pathPrefix / "order")
      .in(paging)
      .in(timeWindow)
      .in(jsonBody[HistoryApiQuery])
      .out(jsonBody[OrderHistoryResponse])
      .tag(pathPrefix)
      .name("Orders history")
      .description("Provides orders history with different filters by given addresses")
}
