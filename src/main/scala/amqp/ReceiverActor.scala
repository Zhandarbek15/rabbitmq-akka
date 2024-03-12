package amqp

import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{AMQP, Channel, DefaultConsumer, Envelope}


class ReceiverActor(channel: Channel, queueName: String, handle:Message=>Unit) extends Actor with ActorLogging {
  private val consumer = new DefaultConsumer(channel) {
    override def handleDelivery(consumerTag: String,
                                envelope: Envelope,
                                properties: AMQP.BasicProperties,
                                body: Array[Byte]): Unit = {
      val message = new String(body, "UTF-8")
      log.info(s"${self.path.name} актор получил сообщение: $message. Ключ роутинга: ${envelope.getRoutingKey}")
      handle(Message(message,envelope.getRoutingKey,properties.getReplyTo,properties.getCorrelationId))
    }
  }
  
  consumerTag = channel.basicConsume(queueName, true, consumer)
  
  private var consumerTag:String = _

  override def receive: Receive = {
    // Начинаем прослушивание очереди
    case "Listen" =>
      log.info(s"Актор ${self.path.name} начинает прослушивание очереди под названием $queueName")
      consumerTag = channel.basicConsume(queueName, true, consumer)
    case "Unlisted" =>
      log.info(s"Актор ${self.path.name} останавливает прослушивание очереди под названием $queueName")
      channel.basicCancel(consumerTag)
  }
}

object ReceiverActor {
  def props(channel: Channel, queueName: String,handle: Message=>Unit): Props = 
    Props(new ReceiverActor(channel, queueName, handle))
}