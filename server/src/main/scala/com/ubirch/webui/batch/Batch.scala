package com.ubirch.webui.batch

import java.io.{ BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader }
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }

import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.consumer.WithConsumerShutdownHook
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.producer.WithProducerShutdownHook
import com.ubirch.util.JsonHelper
import com.ubirch.webui.core.structure.AddDevice
import com.ubirch.webui.core.structure.member.{ DeviceCreationState, UserFactory }
import com.ubirch.webui.server.config.ConfigBase
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization._
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.servlet.FileItem

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

sealed trait Batch[D] {
  val value: Symbol
  val separator: String
  def addDeviceFromBatchRequest(batchRequest: BatchRequest): Either[String, AddDevice]
  def data(line: String, separator: String): Either[String, D]
  def ingest(fileItem: FileItem, withHeader: Boolean, description: String, batchType: Symbol, tags: String)(implicit session: Session): ReadStatus
}

case class ReadStatus(status: Boolean, processed: Int, success: Int, failure: Int, failures: List[String])

object Batch extends StrictLogging {
  def isValid(value: String): Boolean = fromString(value).isDefined
  def fromString(value: String): Option[Batch[_]] = options.find(_.value.name == value)
  def fromSymbol(value: Symbol): Option[Batch[_]] = options.find(_.value == value)
  val options: List[Batch[_]] = List(SIM)

  def read(inputStream: InputStream, skipHeader: Boolean)(f: String => Either[String, _]): ReadStatus = {

    var isr: InputStreamReader = null
    var br: BufferedReader = null

    var processed = 0
    var success = 0
    var failure = 0

    val failureMessages = scala.collection.mutable.ListBuffer.empty[String]

    try {
      isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
      br = new BufferedReader(isr)
      val it = br.lines().iterator().asScala

      while (it.hasNext) {

        if (skipHeader && processed == 0) {}
        else {
          val value = it.next()
          if (value.nonEmpty) {
            f(value) match {
              case Right(_) => success = success + 1
              case Left(error) =>
                failure = failure + 1
                failureMessages += error
            }
          }
        }

        processed = processed + 1
      }

      ReadStatus(status = true, processed, success, failure, failureMessages.toList)

    } catch {
      case e: Exception =>
        logger.error("Error processing stream [{}]", e.getMessage)
        ReadStatus(status = false, processed, success, failure, failureMessages.toList)
    } finally {
      if (br != null) br.close()
      if (isr != null) isr.close()
    }

  }

}

trait WithShutdownHook extends WithConsumerShutdownHook with WithProducerShutdownHook {
  ek: ExpressKafka[_, _, _] =>

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      val countDownLatch = new CountDownLatch(1)
      (for {
        _ <- hookFunc(consumerGracefulTimeout, consumption)()
        _ <- hookFunc(production)()
      } yield ())
        .onComplete {
          case Success(_) => countDownLatch.countDown()
          case Failure(e) =>
            logger.error("Error running jvm hook={}", e.getMessage)
            countDownLatch.countDown()
        }

      val res = countDownLatch.await(5000, TimeUnit.SECONDS) //Waiting 5 secs
      if (!res) logger.warn("Taking too much time shutting down :(  ..")
      else logger.info("Bye bye, see you later...")
    }
  })
}

object Elephant extends ExpressKafka[String, SessionBatchRequest, List[DeviceCreationState]] with WithShutdownHook with ConfigBase {

  implicit val formats: Formats = DefaultFormats
  override implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[SessionBatchRequest] = (_: String, data: Array[Byte]) => {
    read[SessionBatchRequest](new ByteArrayInputStream(data))
  }
  override val consumerTopics: Set[String] = conf.getString("batch.kafkaConsumer.topic").split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[SessionBatchRequest] = (_: String, data: SessionBatchRequest) => {
    write(data).getBytes(StandardCharsets.UTF_8)
  }
  override val consumerBootstrapServers: String = conf.getString("batch.kafkaConsumer.bootstrapServers")
  override val consumerGroupId: String = conf.getString("batch.kafkaConsumer.groupId")
  override val consumerMaxPollRecords: Int = conf.getInt("batch.kafkaConsumer.maxPollRecords")
  override val consumerGracefulTimeout: Int = conf.getInt("batch.kafkaConsumer.gracefulTimeout")
  override val producerBootstrapServers: String = conf.getString("batch.kafkaProducer.bootstrapServers")
  override val metricsSubNamespace: String = conf.getString("batch.kafkaConsumer.metricsSubNamespace")
  override val consumerReconnectBackoffMsConfig: Long = conf.getLong("batch.kafkaConsumer.reconnectBackoffMsConfig")
  override val consumerReconnectBackoffMaxMsConfig: Long = conf.getLong("batch.kafkaConsumer.reconnectBackoffMaxMsConfig")
  override val lingerMs: Int = conf.getInt("batch.kafkaProducer.lingerMS")
  override val maxTimeAggregationSeconds: Long = 120
  val producerTopic: String = conf.getString("batch.kafkaProducer.topic")

  override def process: Process = Process.async { crs =>

    val result = crs.groupBy(x => x.value().session)
      .map { case (session, sessionBatchRequest) =>

        val user = UserFactory.getByUsername(session.username)(session.realm)

        val devices = sessionBatchRequest.toList
          .flatMap { sb =>
            val br = sb.value().batchRequest
            Batch.fromSymbol(br.batchType)
              .map { b =>
                b.addDeviceFromBatchRequest(br)
              }.getOrElse(Left("Unknown batch_type")) match {
                case Right(value) =>
                  List(value)
                case Left(value) =>
                  logger.error("{}", value)
                  Nil
              }
          }

        user.createMultipleDevicesAsync(devices)

      }

    Future.sequence(result).map(_.toList.flatten)

  }
}

case class SIMData(provider: String, imsi: String, pin: String, cert: String)

case object SIM extends Batch[SIMData] with ConfigBase with StrictLogging {

  import Elephant.{ producerTopic, send }

  override val value: Symbol = 'sim_import

  override val separator: String = conf.getString("batch.separator")

  override def addDeviceFromBatchRequest(batchRequestData: BatchRequest): Either[String, AddDevice] = {

    val data = try {
      Right(JsonHelper.FromJson[SIMData](batchRequestData.data).get)
    } catch {
      case e: Exception =>
        Left("Error parsing data: " + e.getMessage)
    }

    data.right.map { value =>
      AddDevice(UUID.randomUUID().toString, batchRequestData.description, attributes =
        Map(
          "pin" -> List(value.pin),
          "imsi" -> List(value.imsi),
          "provider" -> List(value.provider),
          "cert_id" -> List("This is an ID"),
          "batch_type" -> List(batchRequestData.batchType.name),
          "filename" -> List(batchRequestData.filename),
          "tags" -> List(batchRequestData.tags)
        ))
    }

  }

  override def data(line: String, separator: String): Either[String, SIMData] = {
    line.split(separator).toList match {
      case List(provider, imsi, pin, cert) if provider.nonEmpty && imsi.nonEmpty && pin.nonEmpty && cert.nonEmpty =>
        Right(SIMData(provider, imsi, pin, cert))
      case _ =>
        logger.error("Error processing line [{}]", line)
        Left(s"Error processing line [$line]")
    }
  }

  override def ingest(fileItem: FileItem, skipHeader: Boolean, description: String, batchType: Symbol, tags: String)(implicit session: Session): ReadStatus = {
    val readStatus = Batch.read(fileItem.getInputStream, skipHeader) { line =>
      data(line, separator).map { d =>
        val jv = JsonHelper.ToJson[SIMData](d).get
        val sbr = BatchRequest(fileItem.name, description, batchType, tags, jv).withSession
        send(producerTopic, sbr)
      }
    }

    readStatus

  }

}

case class BatchRequest(filename: String, description: String, batchType: Symbol, tags: String, data: JValue) {
  def withSession(implicit session: Session): SessionBatchRequest = SessionBatchRequest(session, this)
}
case class Session(id: String, realm: String, username: String)
case class SessionBatchRequest(session: Session, batchRequest: BatchRequest)

