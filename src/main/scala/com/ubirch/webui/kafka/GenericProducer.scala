package com.ubirch.webui.kafka

import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.webui.config.ConfigBase
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{ ByteArraySerializer, Serializer, StringSerializer }

import scala.concurrent.Future

object GenericProducer extends ConfigBase {

  object ConfPaths {
    final val PRODUCER_BOOTSTRAP_SERVERS: String = "kafkaProducer.bootstrapServers"
    final val IDENTITY_PRODUCER_TOPIC: String = "kafkaProducer.identityTopic"
    final val IDENTITY_ACTIVATION_PRODUCER_TOPIC: String = "kafkaProducer.identityActivationTopic"
    final val LINGER_MS: String = "kafkaProducer.lingerMS"
  }

  final val ACTIVATION_PRODUCER_TOPIC: String = conf.getString(ConfPaths.IDENTITY_ACTIVATION_PRODUCER_TOPIC)
  final val IDENTITY_PRODUCER_TOPIC: String = conf.getString(ConfPaths.IDENTITY_PRODUCER_TOPIC)

  final val production: ExpressProducer[String, Array[Byte]] = new ExpressProducer[String, Array[Byte]] {
    override val keySerializer: Serializer[String] = new StringSerializer
    override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer()
    override val producerBootstrapServers: String = conf.getString(ConfPaths.PRODUCER_BOOTSTRAP_SERVERS)
    override val lingerMs: Int = conf.getInt(ConfPaths.LINGER_MS)
    override val production: ProducerRunner[String, Array[Byte]] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
  }

  def send(topic: String, value: Array[Byte]): Future[RecordMetadata] = production.send(topic, value)

}
