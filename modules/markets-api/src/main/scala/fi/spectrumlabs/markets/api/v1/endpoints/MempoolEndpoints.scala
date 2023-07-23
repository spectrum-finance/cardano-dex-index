package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, UserOrderInfo}
import sttp.tapir.Endpoint
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

object MempoolEndpoints {

  val pathPrefix = "mempool"

  def endpoints: List[Endpoint[_, _, _, _]] = orderMempoolE :: Nil

  def orderMempoolE: Endpoint[HistoryApiQuery, HttpError, List[UserOrderInfo], Any] =
    baseEndpoint.post
      .in(pathPrefix / "order")
      .in(jsonBody[HistoryApiQuery])
      .out(jsonBody[List[UserOrderInfo]])
      .tag(pathPrefix)
      .name("Mempool orders by addresses")
      .description("Mempool orders by addresses")
}
