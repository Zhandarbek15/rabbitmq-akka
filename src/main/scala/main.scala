
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import akka.actor.{ActorRef, ActorSystem, Props}
import amqp.{AmqpActor, Message, RabbitMQ}
import akka.pattern.ask
import akka.util.Timeout

import java.util.concurrent.TimeUnit
import scala.concurrent.Future.never.onComplete
import scala.language.postfixOps
import scala.util.{Failure, Success}

@main
def main(): Unit = {
  implicit val system = ActorSystem("rabbitmq")
  implicit val ec:ExecutionContext = system.dispatcher
  implicit val timeout:Timeout = Timeout(FiniteDuration.apply(10,TimeUnit.MINUTES))

  val amqp = system.actorOf(Props(new AmqpActor("X:testExchange")),"mainActor")


  val response = (amqp ? RabbitMQ.Ask("test.ask","Response1 2000")).mapTo[String]

  response onComplete {
    case Success(value) => println(s"Пришло ответ $value")
    case Failure(exception) => println(exception.getMessage)
  }

  val response1 = (amqp ? RabbitMQ.Ask("test.ask", "Response2 1000")).mapTo[String]

  response1 onComplete {
    case Success(value) => println(s"Пришло ответ $value")
    case Failure(exception) => println(exception.getMessage)
  }

  amqp ! RabbitMQ.DeclareListener("testRequestQueue1","Listener1",
    message =>{
      if(message.replyTo != null && message.correlationId != null){
        val m =  message.body.split(" ")
        Thread.sleep(m(1).toInt)
        val response = "*********Response: - " + message.body
        amqp ! RabbitMQ.Answer(message.replyTo,message.correlationId,response)
      }
    }
  )
}