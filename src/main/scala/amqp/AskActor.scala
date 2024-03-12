package amqp

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.client.{AMQP, Channel, DefaultConsumer, Envelope}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Try
import scala.collection.mutable.*

class AskActor(channel: Channel, exchangeName:String, responseQueueName: String) extends Actor with ActorLogging {

  // Создание очереди для ответов. Имя очереди responseQueueName
  channel.queueDeclare(responseQueueName, false, false, false, null)
  channel.queueBind(responseQueueName,"X:testExchange",s"response.$responseQueueName")

  // Сохранение ссылок на акторы которые запрашивали
  private var sendors: mutable.Map[String, ActorRef] = mutable.Map()


  // Создание потребителя
  val consumer = new DefaultConsumer(channel) {
    override def handleDelivery(
                                 consumerTag: String,
                                 envelope: Envelope,
                                 properties: AMQP.BasicProperties,
                                 body: Array[Byte]
                               ): Unit = {
      // TODO: Проверить на очередность выполнения. Чтобы одновременно не изменять sendors
      val response = new String(body, "UTF-8")
      val correlationId = properties.getCorrelationId

      val actorRef: Option[ActorRef] = sendors.remove(correlationId)
      if (sendors.isEmpty) {
        log.info(s"Слушатель ответа актора ${self.path.name} останавливает слушать потому что нет активных запросов!")
        channel.basicCancel(consumerTag)
      }
      actorRef match
        case Some(actor: ActorRef) =>
          log.info(s"При возвращений ответа с идентификатором $correlationId был направлен на актор ${actor.path.name}")
          actor ! response
        case None =>
          log.info(s"При возвращений ответа с идентификатором $correlationId не найдено отправителья")
    }
  }

  override def receive: Receive = {
    case RabbitMQ.Ask(routingKey,content) =>
      // Создание уникального идентификатора для запроса
      val requestId = java.util.UUID.randomUUID().toString

      // Настройки отправляемого сообщения
      val properties = new AMQP.BasicProperties.Builder()
        // В какой очередь нужно вернуться
        .replyTo(s"response.$responseQueueName")
        // Идентификатор сообщения
        .correlationId(requestId)
        .build()

      if(sendors.isEmpty) {
        log.info(s"Слушатель ответа актора ${self.path.name} начинает слушать!")
        channel.basicConsume(responseQueueName,true,consumer)
      }

      // Сохраняем ключ значение как айди и его отправитель,
      // чтобы вернуть правильное сообщение к отправителью
      sendors += (requestId -> sender())
      log.info(s"Был отправлен запрос с ожиданием ответа по ключу $routingKey с идентификатором $requestId")
      channel.basicPublish(exchangeName, routingKey, properties, content.getBytes("UTF-8"))
  }
}

object AskActor {
  def props(channel: Channel, exchangeName:String, responseQueueName: String): Props 
  = Props(new AskActor(channel, exchangeName, responseQueueName))
}
