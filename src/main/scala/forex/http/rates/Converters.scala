package forex.http.rates
import forex.domain.{Rate, Timestamp}
import forex.programs.rates.errors.Error

import java.time.{OffsetDateTime, ZoneId}
object Converters {
  import Protocol._

  private[rates] implicit class GetApiResponseOps(val rate: Rate) extends AnyVal {
    def asGetApiResponse: GetApiResponse =
      GetApiResponse(
        from = rate.pair.from,
        to = rate.pair.to,
        price = rate.price,
        timestamp = rate.timestamp
      )
  }

  private[rates] implicit class GetApiErrorResponseOps(val error: Error) extends AnyVal {
    def asGetApiErrorResponse: GetApiErrorResponse =
      GetApiErrorResponse(
        message = error.getMessage,
        timestamp = Timestamp(OffsetDateTime.now(ZoneId.of("Z")))
      )
  }
}
