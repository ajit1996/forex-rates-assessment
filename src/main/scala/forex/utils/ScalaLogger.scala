package forex.utils

import com.typesafe.scalalogging.{CanLog, Logger}

class ScalaLogger {
  val logger = Logger("OneFrameInterpreter")
}

case class RequestId(id: String)

object LoggingImplicits {
  implicit case object WithRequestId extends CanLog[RequestId] {
    override def logMessage(originalMsg: String, a: RequestId): String =
      s"[REQ: ${a.id}] $originalMsg"
  }
}
