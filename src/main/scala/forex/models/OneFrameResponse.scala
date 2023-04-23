package forex.models

import java.time.ZonedDateTime

final case class OneFrameResponse(
    from: String,
    to: String,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    timestamp: ZonedDateTime
)

object SlickTables {

  import slick.jdbc.PostgresProfile.api._
  class OneFrameResponseTable(tag: Tag) extends Table[OneFrameResponse](tag, Some("forex"), "Rates") {
    def from       = column[String]("from")
    def to         = column[String]("to")
    def bid        = column[BigDecimal]("bid")
    def ask        = column[BigDecimal]("ask")
    def price      = column[BigDecimal]("price")
    def timestamp  = column[ZonedDateTime]("time_stamp")
    override def * = (from, to, bid, ask, price, timestamp) <> (OneFrameResponse.tupled, OneFrameResponse.unapply)
    def pk         = primaryKey("pk_currency_pair", (from, to))
  }
  lazy val oneFrameResponseTable = TableQuery[OneFrameResponseTable]
}
