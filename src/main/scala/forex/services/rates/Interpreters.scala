package forex.services.rates

import cats.Applicative
import interpreters._

object Interpreters {
  def getRates[F[_]: Applicative]: Algebra[F] = new OneFrameInterpreter[F]()
}