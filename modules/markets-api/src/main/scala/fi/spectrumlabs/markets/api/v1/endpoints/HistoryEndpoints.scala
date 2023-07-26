package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, PendingNeedRefundResponse}
import sttp.tapir.Endpoint
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

object HistoryEndpoints {

  val pathPrefix = "history"

  def endpoints: List[Endpoint[_, _, _, _]] = orderHistoryE :: orderHistoryV2E :: historyPendingNeedRefundE :: Nil

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

  def orderHistoryV2E: Endpoint[(Paging, TimeWindow, HistoryApiQuery), HttpError, OrderHistoryResponse, Any] =
    baseEndpoint.post
      .in(pathPrefix / "order" / "v2")
      .in(paging)
      .in(timeWindow)
      .in(jsonBody[HistoryApiQuery])
      .out(jsonBody[OrderHistoryResponse])
      .tag(pathPrefix)
      .name("Orders history")
      .description("Provides orders history with different filters by given addresses")

  def historyPendingNeedRefundE: Endpoint[HistoryApiQuery, HttpError, PendingNeedRefundResponse, Any] =
    baseEndpoint.post
      .in(pathPrefix / "order" / "pending")
      .in(jsonBody[HistoryApiQuery])
      .out(jsonBody[PendingNeedRefundResponse])
      .tag(pathPrefix)
      .name("Orders pending/need refund")
}
