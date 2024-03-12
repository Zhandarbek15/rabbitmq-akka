package amqp

import akka.pattern.Patterns.*
import akka.actor.{Actor, ActorLogging, ActorRef}
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}

class AmqpActor(exchangeName: String) extends Actor with ActorLogging {
  private var connection: Connection = _
  private var channel: Channel = _

  private var senderActor : ActorRef = _
  private var askerActor : ActorRef = _

  override def preStart(): Unit = {

    // Создаем соединение с RabbitMQ
    val factory = new ConnectionFactory()
    factory.setHost("localhost")
    factory.setPort(5672)
    factory.setUsername("guest")
    factory.setPassword("guest")
    connection = factory.newConnection()

    channel = connection.createChannel()
    log.info("Соединение с RabbitMQ установлено")

    senderActor = context.actorOf(SenderActor.props(channel,exchangeName), "sender")
    askerActor = context.actorOf(AskActor.props(channel,exchangeName, "responseQueue1"), "asker")

    log.info("Созданы акторы отправителья и запросителья")
  }


  override def receive: Receive = {
    case msg@RabbitMQ.Tell(routingKey, content) =>
      senderActor forward msg
      log.info(s"Сообщение под ключом $routingKey направлено в актор под названием ${senderActor.path.name}")
      
    case msg@RabbitMQ.Answer(routingKey,correlationId,content)=>
      senderActor forward msg
      log.info(s"Ответ для $routingKey направлено в актор под названием ${senderActor.path.name}")
      
    case msg@RabbitMQ.Ask(routingKey, content) =>
      // Запрос передаем дальше
      askerActor forward msg
      log.info(s"Запрос под ключом $routingKey направлено в актор под названием ${askerActor.path.name}")

    case RabbitMQ.DeclareListener(queue,actorName, handle) =>
      // Создаем актора слушателья и возвращаем его ссылку
      context.actorOf(ReceiverActor.props(channel,queue,handle),actorName)
      log.info(s"Подписка на очередь $queue установлена. " +
        s"Ссылку на актор можно получит по пути 'user/${self.path.name}/$actorName'")
      
  }

  override def postStop(): Unit = {
    // Закрываем соединение с RabbitMQ
    channel.close()
    connection.close()

    log.info("Соединение с RabbitMQ закрыто")
  }
}
