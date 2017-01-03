package service.actor

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import akka.remote.DisassociatedEvent
import akka.routing.RoundRobinPool
import com.typesafe.config.{ConfigValueFactory, ConfigObject}
import spray.can.Http
import spray.http.{HttpResponse, HttpRequest}


class Router extends Actor {
  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case r@HttpRequest(_, _, _, _, _) => {
//      println(s"sender: ${sender.path.toString}, self: ${context.self.path}")

      val actor = Router.getActorRefByRequest(r)
      val requestSender = new RequestSender(r, sender)
      actor ! requestSender
    }
  }
}

class NotFound extends Actor {
  def receive = {
    case r: RequestSender => {
      r.sender ! HttpResponse(status = 404)
    }
  }
}

class RequestSender(val request: HttpRequest, val sender: ActorRef) extends Serializable

object Router {
  import com.typesafe.config.ConfigFactory

  val conf = ConfigFactory.load()
  val actorSystem = conf.getString("service.actor.system")
  val system = ActorSystem(actorSystem)

  val router = system.actorOf(Props[Router], "Router")
  system.eventStream.subscribe(router, classOf[DisassociatedEvent])

  val notFoundActor = system.actorOf(Props[NotFound], "notFound")

  // Default Actor
  val defaultActor = system.actorOf(Props.create(Class.forName(s"service.actor.${conf.getString("service.actor.default")}")), "default")

  // Path - Actor Map
  val actorConfigs = conf.getList("service.actors").toArray()
  val actorMap = actorConfigs.map(x => {
    val path = x.asInstanceOf[ConfigObject].get("path").render().replaceAll("\"","")
    val name = x.asInstanceOf[ConfigObject].get("name").render().replaceAll("\"","")
    val size = x.asInstanceOf[ConfigObject].getOrDefault("size", ConfigValueFactory.fromAnyRef(1)).render().replaceAll("\"","").toInt

    try {
      val prop = Props.create(Class.forName(s"service.actor.${name}"))
      (path, system.actorOf(if (size == 1) prop else RoundRobinPool(size).props(prop), path.replaceAll("/", "")))
    } catch {
      case e: Exception => {
        (path, notFoundActor)
      }
    }
  }).toMap

  def getActorRef(name: String) = {
    actorMap.getOrElse(name, defaultActor)
  }

  def getActorRefByRequest(r : HttpRequest) = {
    val path = if (r.uri.path.startsWithSlash) r.uri.path.toString else r.uri.path.toString
    val actor = this.getActorRef(path)
    actor
  }

}