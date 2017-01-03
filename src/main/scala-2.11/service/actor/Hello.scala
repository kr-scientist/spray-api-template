package service.actor

import akka.actor.Actor
import spray.http.HttpResponse

/**
  * Created by aram on 2016. 12. 8..
  */
class Hello extends Actor {
  def receive = {
    case r: RequestSender => {
      r.sender ! HttpResponse(status = 200, entity = "Hello, World!")
    }
  }
}
