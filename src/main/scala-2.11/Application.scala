import akka.io.IO
import akka.util.Timeout
import spray.can.Http
import akka.pattern.ask
import scala.concurrent.duration._
import service.actor.Router


object Global extends App {
  implicit val system = Router.system
  implicit val timeout = Timeout(5.seconds)
  IO(Http) ? Http.Bind(Router.router, interface = "localhost", port = 8080)
}
