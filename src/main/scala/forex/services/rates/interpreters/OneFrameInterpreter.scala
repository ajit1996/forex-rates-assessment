package forex.services.rates.interpreters

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import cats.Applicative
import cats.syntax.applicative._
import cats.syntax.either._
import com.typesafe.config.ConfigFactory
import forex.domain.Rate.Pair
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.models._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error.{OneFrameLookupFailed, ParsingFailed, SameCurrencyConversionFailed, UnsupportedCurrencyError}
import forex.services.rates.errors._
import forex.utils.{DbUtils, ScalaLogger}
import io.circe.{Decoder, HCursor, Json, ParsingFailure, jawn, parser}

import java.time.temporal.ChronoUnit._
import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
class OneFrameInterpreter[F[_]: Applicative] extends Algebra[F] {

  private val scalaLogger: ScalaLogger = new ScalaLogger
  val executor: ExecutorService        = Executors.newFixedThreadPool(4)
  implicit val ec: ExecutionContext    = ExecutionContext.fromExecutorService(executor)
  implicit val oneFrameResponseJsonDecoder: Decoder[OneFrameResponse] = (c: HCursor) =>
    for {
      from <- c.downField("from").as[String]
      to <- c.downField("to").as[String]
      bid <- c.downField("bid").as[BigDecimal]
      ask <- c.downField("ask").as[BigDecimal]
      price <- c.downField("price").as[BigDecimal]
      zonedDateTime <- c.downField("time_stamp").as[ZonedDateTime]
    } yield {
      OneFrameResponse(
        from,
        to,
        bid,
        ask,
        price,
        zonedDateTime
      )
  }
  override def get(pair: Rate.Pair): F[Error Either Rate] =
    getValidCurrencyConversionRates(pair.from, pair.to) match {
      case Left(rate)   => rate.asRight[Error].pure[F]
      case Right(error) => error.asLeft[Rate].pure[F]
    }

  private def getValidCurrencyConversionRates(fromCurrency: Currency, toCurrency: Currency): Either[Rate, Error] = {
    if (isInvalidCurrency(fromCurrency))
      Right(UnsupportedCurrencyError(s"Un-supported/Invalid from_currency"))
    else if (isInvalidCurrency(toCurrency))
      Right(UnsupportedCurrencyError(s"Un-supported/Invalid to_currency"))
    else if (fromCurrency.equals(toCurrency))
      Right(SameCurrencyConversionFailed(s"Same from_currency - $fromCurrency and to_currency - $toCurrency can't be converted"))
    else
      getCurrencyConversionRates(fromCurrency, toCurrency)
  }
  private def getCurrencyConversionRates(fromCurrency: Currency, toCurrency: Currency): Either[Rate, Error] = {
    val currencyPair: String = fromCurrency.toString + toCurrency.toString
    if (isOneFrameResponsePresentInDb(fromCurrency, toCurrency)) {
      val oneFrameResponse = fetchOneFrameResponseFromDb(fromCurrency, toCurrency)
      if (isOneFrameResponseWithinRecentFiveMinutes(oneFrameResponse)) {
        scalaLogger.logger.info("one-frame API response for from_currency {} and to_currency {} fetched and returned from db", fromCurrency, toCurrency)
        convertOneFrameResponseWithCurrentTimestampToRateDto(oneFrameResponse)
      } else {
        scalaLogger.logger.info("one-frame API response for from_currency {} and to_currency {}", fromCurrency, toCurrency)
        val oneFrameApiResponseOrError = fetchCurrencyConversionRatesFromOneFrameApi(currencyPair)
        oneFrameApiResponseOrError.left.map(updateOneFrameResponseInDb)
        oneFrameApiResponseOrError.left.map(convertOneFrameResponseToRateDto)
      }
    } else {
      scalaLogger.logger.info("one-frame API response for from_currency {} and to_currency {}", fromCurrency, toCurrency)
      val oneFrameApiResponseOrError = fetchCurrencyConversionRatesFromOneFrameApi(currencyPair)
      oneFrameApiResponseOrError.left.map(saveOneFrameResponseInDb)
      oneFrameApiResponseOrError.left.map(convertOneFrameResponseToRateDto)
    }
  }

  private def isInvalidCurrency(fromCurrency: Currency) = Currency.NA.equals(fromCurrency)

  private def isOneFrameResponseWithinRecentFiveMinutes(oneFrameResponse: OneFrameResponse) = {
    val dateTime = oneFrameResponse.timestamp
    MINUTES.between(dateTime.toLocalDateTime, LocalDateTime.now(dateTime.getZone)) < 5
  }

  private def fetchCurrencyConversionRatesFromOneFrameApi(currencyPair: String): Either[OneFrameResponse, Error] = {
    scalaLogger.logger.info("Inside fetch currency rates for pair - {}", currencyPair)
    implicit val system: ActorSystem             = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val timeout                              = 40.seconds
    val responseFuture: Future[HttpResponse] = Http().singleRequest(prepareOneFrameApiRequest(currencyPair))
    val responseAsString: Future[String]     = responseFuture.flatMap(response => Unmarshal(response.entity).to[String])

    def parseOneFrameApiResponse(json: Json): Either[OneFrameResponse, OneFrameLookupFailed] =
      jawn.decode[List[OneFrameResponse]](json.toString()) match {
        case Left(error) =>
          scalaLogger.logger.error("Got error from one-frame API ::, ERROR {}", error.getMessage)
          Right(OneFrameLookupFailed(error.getMessage))
        case Right(response) =>
          val oneFrameResponse: OneFrameResponse = response.last
          scalaLogger.logger.info("Successfully got response from one-frame API at {}", oneFrameResponse.timestamp)
          Left(oneFrameResponse)
      }

    Await.result(
      responseAsString.map(response => {
        val failureOrJson: Either[ParsingFailure, Json] = parser.parse(response)
        failureOrJson match {
          case Left(error) =>
            scalaLogger.logger.error("Error occurred while parsing one-frame response ::, ERROR {}", error.getMessage)
            Right(ParsingFailed(error.getMessage()))
          case Right(json) =>
            scalaLogger.logger.info("Successfully parsed one-frame API response")
            parseOneFrameApiResponse(json)
        }
      }),
      timeout
    )
  }

  private def isOneFrameResponsePresentInDb(fromCurrency: Currency, toCurrency: Currency) = DbUtils.isOneFrameResponsePresent(fromCurrency.toString, toCurrency.toString)
  private def fetchOneFrameResponseFromDb(fromCurrency: Currency, toCurrency: Currency) = DbUtils.fetchOneFrameResponse(fromCurrency.toString, toCurrency.toString)
  private def saveOneFrameResponseInDb(oneFrameResponse: OneFrameResponse): OneFrameResponse = DbUtils.saveOrUpdateOneFrameResponse(oneFrameResponse)
  private def updateOneFrameResponseInDb(oneFrameResponse: OneFrameResponse): OneFrameResponse = DbUtils.saveOrUpdateOneFrameResponse(oneFrameResponse)
  private def convertOneFrameResponseToRateDto(oneFrameResponse: OneFrameResponse): Rate = {
    val from                           = Currency.fromString(oneFrameResponse.from)
    val to                             = Currency.fromString(oneFrameResponse.to)
    val pair                           = Pair(from, to)
    val price                          = Price(oneFrameResponse.price)
    val offsetDateTime: OffsetDateTime = OffsetDateTime.now(oneFrameResponse.timestamp.getZone)
    val timestamp: Timestamp           = Timestamp(offsetDateTime)
    Rate(pair, price, timestamp)
  }

  private def convertOneFrameResponseWithCurrentTimestampToRateDto(oneFrameResponse: OneFrameResponse): Either[Rate, Error] = {
    val from                           = Currency.fromString(oneFrameResponse.from)
    val to                             = Currency.fromString(oneFrameResponse.to)
    val pair                           = Pair(from, to)
    val price                          = Price(oneFrameResponse.price)
    val offsetDateTime: OffsetDateTime = OffsetDateTime.now(oneFrameResponse.timestamp.getZone)
    val timestamp: Timestamp           = Timestamp(offsetDateTime)
    Left(Rate(pair, price, timestamp))
  }

  def prepareOneFrameApiRequest(currencyPair: String): HttpRequest = {
    val appConfig         = ConfigFactory.load().getConfig("app")
    val oneFrameApiConfig = appConfig.getConfig("one-frame-api")
    val host              = oneFrameApiConfig.getString("host")
    val port              = oneFrameApiConfig.getString("port")
    val token             = oneFrameApiConfig.getString("token")
    HttpRequest(
      method = HttpMethods.GET,
      uri = s"http://$host:$port/rates?pair=$currencyPair",
    ).addHeader(RawHeader("token", token))
  }
}
