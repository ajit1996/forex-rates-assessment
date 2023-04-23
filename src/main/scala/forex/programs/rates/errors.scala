package forex.programs.rates

import forex.programs.rates.errors.Error.{CurrencyLookupFailed, InvalidCurrencyExchangeRequest, ParsingFailed, RateLookupFailed, TimeoutFailed}
import forex.services.rates.errors.{Error => RatesServiceError}

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(message: String) extends Exception(message) with Error
    final case class CurrencyLookupFailed(message: String) extends Exception(message) with Error
    final case class InvalidCurrencyExchangeRequest(message: String) extends Exception(message) with Error
    final case class ParsingFailed(message: String) extends Exception(message) with Error
    final case class TimeoutFailed(message: String) extends Exception(message) with Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.UnsupportedCurrencyError(msg)     => CurrencyLookupFailed(msg)
    case RatesServiceError.OneFrameLookupFailed(msg)         => RateLookupFailed(msg)
    case RatesServiceError.ParsingFailed(msg)                => ParsingFailed(msg)
    case RatesServiceError.TimeoutFailed(msg)                => TimeoutFailed(msg)
    case RatesServiceError.SameCurrencyExchangeNotAllowed(msg) => InvalidCurrencyExchangeRequest(msg)
  }
}
