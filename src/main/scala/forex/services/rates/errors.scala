package forex.services.rates

object errors {

  sealed trait Error
  object Error {
    final case class OneFrameLookupFailed(msg: String) extends Error
    final case class UnsupportedCurrencyError(msg: String) extends Error
    final case class SameCurrencyConversionFailed(msg: String) extends Error
    final case class ParsingFailed(msg: String) extends Error
    final case class TimeoutFailed(msg: String) extends Error
  }
}
