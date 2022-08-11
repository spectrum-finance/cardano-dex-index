package fi.spectrumlabs.markets.api.v1.endpoints

import fi.spectrumlabs.core.network.models.HttpError
import sttp.tapir._

object OpenApiEndpoints {

  private val PathPrefix = "docs"
  private val Group      = "docs"

  def endpoints: List[Endpoint[_, _, _, _]] = apiSpecDef :: Nil

  def apiSpecDef: Endpoint[Unit, HttpError, String, Any] =
    baseEndpoint
      .in(PathPrefix / "openapi")
      .out(plainBody[String])
      .tag(Group)
      .name("Openapi route")
      .description("Allow to get openapi.yaml")
}
