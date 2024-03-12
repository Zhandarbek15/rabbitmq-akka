package amqp

import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{AMQP, Channel}


class SenderActor(channel: Channel, exchangeName:String) extends Actor with ActorLogging {

  override def receive: Receive = {
    case RabbitMQ.Tell(routingKey, message) =>
      // Преобразуем сообщение в массив байтов
      val messageBytes = message.getBytes("UTF-8")

      // Отправляем сообщение в очередь
      channel.basicPublish(exchangeName, routingKey, new AMQP.BasicProperties(), messageBytes)

    case RabbitMQ.Answer(routingKey,correlationId,message) =>
      // Преобразуем сообщение в массив байтов
      val messageBytes = message.getBytes("UTF-8")

      val properties = new AMQP.BasicProperties.Builder()
        .correlationId(correlationId)
        .build()
      
      // Отправляем сообщение в очередь
      channel.basicPublish(exchangeName, routingKey,properties, messageBytes)
  }
}

object SenderActor {
  def props(channel: Channel, exchangeName: String): Props = Props(new SenderActor(channel,exchangeName))
}