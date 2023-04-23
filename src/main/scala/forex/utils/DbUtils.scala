package forex.utils
import forex.config.Connection
import forex.models.{ OneFrameResponse, SlickTables }

import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext, Future }

object DbUtils {
  import slick.jdbc.PostgresProfile.api._

  val executor                      = Executors.newFixedThreadPool(4)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)

  def isOneFrameResponsePresent(from: String, to: String): Boolean = {
    val queryDesc = SlickTables.oneFrameResponseTable.filter(_.from === from).filter(_.to === to).result
    val resultFuture: Future[Seq[OneFrameResponse]] = Connection.db.run(queryDesc)
    val timeout = 40.seconds
    Await.result(resultFuture.map(response => if (response.size > 0) true else false), timeout)
  }
  def fetchOneFrameResponse(from: String, to: String): OneFrameResponse = {
    val queryDesc                                   = SlickTables.oneFrameResponseTable.filter(_.from === from).filter(_.to === to).result
    val resultFuture: Future[Seq[OneFrameResponse]] = Connection.db.run(queryDesc)
    val timeout                                     = 40.seconds
    Await.result(resultFuture.map(response => Some { response.head }), timeout).value
  }
  def saveOrUpdateOneFrameResponse(oneFrameResponse: OneFrameResponse): OneFrameResponse = {
    val queryDesc                 = SlickTables.oneFrameResponseTable.insertOrUpdate(oneFrameResponse)
    val resultFuture: Future[Int] = Connection.db.run(queryDesc)
    val timeout                   = 40.seconds
    Await.result(resultFuture.map(_ => oneFrameResponse), timeout)
  }
}
