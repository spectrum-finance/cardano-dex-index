package fi.spectrumlabs.markets.api.v1

import fi.spectrumlabs.core.network.models.HttpError
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{Endpoint, EndpointInput, emptyOutputAs, endpoint, oneOf, oneOfDefaultMapping, oneOfMapping}
import sttp.tapir._
import sttp.tapir.generic.auto._
import fi.spectrumlabs.markets.api.v1.endpoints.models.{Paging, TimeWindow}
import io.circe.generic.auto._
import cats.syntax.option._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

package object endpoints {
  val V1Prefix: EndpointInput[Unit] = "v1"

  val baseEndpoint: Endpoint[Unit, HttpError, Unit, Any] =
    endpoint
      .in(V1Prefix)
      .errorOut(
        oneOf[HttpError](
          oneOfMapping(StatusCode.NotFound, jsonBody[HttpError.NotFound].description("not found")),
          oneOfMapping(StatusCode.NoContent, emptyOutputAs(HttpError.NoContent)),
          oneOfDefaultMapping(jsonBody[HttpError.Unknown].description("unknown"))
        )
      )

  implicit val codec: Codec.PlainCodec[FiniteDuration] = Codec.string
    .mapDecode(fromString)(_.toString)

  private def fromString(s: String): DecodeResult[FiniteDuration] =
    Option(FiniteDuration(Duration(s).toSeconds, SECONDS)) match {
      case Some(value) => DecodeResult.Value(value)
      case None        => DecodeResult.Mismatch("Expected correct ts string", s)
    }

  def after: EndpointInput[FiniteDuration] =
    query[Long]("after")
      .validate(Validator.min(0))
      .validate(Validator.max(Long.MaxValue))
      .map { input =>
        FiniteDuration(Duration(input, TimeUnit.SECONDS).toSeconds, SECONDS)
      }(_.toSeconds)
      .description("Unix Timestamp after which statistic is accumulated.")
      .example(FiniteDuration(Duration(1658379570, TimeUnit.SECONDS).toSeconds, SECONDS))

  def timeWindow: EndpointInput[TimeWindow] =
    (query[Option[Long]]("from")
      .description("Window lower bound (UNIX timestamp millis)")
      .validateOption(Validator.min(0L)) and
      query[Option[Long]]("to")
        .description("Window upper bound (UNIX timestamp millis)")
        .validateOption(Validator.min(0L)))
      .map { input =>
        val from = input._1.map(from => FiniteDuration(Duration(from, TimeUnit.SECONDS).toSeconds, SECONDS).toSeconds)
        val to   = input._2.map(to => FiniteDuration(Duration(to, TimeUnit.SECONDS).toSeconds, SECONDS).toSeconds)
        TimeWindow(from, to)
      } { case TimeWindow(from, to) => from -> to }

  def minutesResolution: EndpointInput[Long] =
    query[Long]("resolution")
      .validate(Validator.min(0))
      .validate(Validator.max(Long.MaxValue))
      .description("Resolution in minutes")

  def paging: EndpointInput[Paging] =
    (query[Option[Int]]("offset").validateOption(Validator.min(0)) and
      query[Option[Int]]("limit").validateOption(Validator.min(1))
        .validateOption(Validator.max(50)))
      .map { input =>
        Paging(input._1.getOrElse(0), input._2.getOrElse(20))
      } { case Paging(offset, limit) => offset.some -> limit.some }
}
