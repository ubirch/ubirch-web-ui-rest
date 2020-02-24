package com.ubirch.webui.batch

import java.io.{ BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader }
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.express.{ ExpressKafka, ExpressProducer, WithShutdownHook }
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.webui.core.structure.AddDevice
import com.ubirch.webui.core.structure.member.{ DeviceCreationState, UserFactory }
import com.ubirch.webui.server.config.ConfigBase
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.json4s.{ Formats, _ }
import org.scalatra.servlet.FileItem

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

sealed trait Batch[D] {
  val value: Symbol
  val separator: String
  def deviceFromBatchRequest(batchRequest: BatchRequest): Either[String, (D, AddDevice)]
  def extractData(line: String, separator: String): Either[String, D]
  def storeCertificateInfo(cert: Any)(implicit ec: ExecutionContext): Future[Either[String, Boolean]]
  def ingest(fileItem: FileItem, withHeader: Boolean, description: String, batchType: Symbol, tags: String)(implicit session: Session): ReadStatus
}

case class ReadStatus(status: Boolean, processed: Int, success: Int, failure: Int, failures: List[String])

object Batch extends StrictLogging {

  lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
  lazy val formats: Formats = Serialization.formats(NoTypeHints) ++ org.json4s.ext.JavaTypesSerializers.all
  val options: List[Batch[_]] = List(SIM)

  def isValid(value: String): Boolean = fromString(value).isDefined
  def fromString(value: String): Option[Batch[_]] = options.find(_.value.name == value)
  def fromSymbol(value: Symbol): Option[Batch[_]] = options.find(_.value == value)

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

  object Configs {
    val FILE_SEPARATOR: String = "batch.separator"

    object Inject {
      val CONSUMER_TOPIC: String = "batch.inject.kafkaConsumer.topic"
      val CONSUMER_BOOTSTRAP_SERVERS: String = "batch.inject.kafkaConsumer.bootstrapServers"
      val CONSUMER_GROUP_ID: String = "batch.inject.kafkaConsumer.groupId"
      val CONSUMER_MAX_POLL_RECORDS: String = "batch.inject.kafkaConsumer.maxPollRecords"
      val CONSUMER_GRACEFUL_TIMEOUT: String = "batch.inject.kafkaConsumer.gracefulTimeout"
      val METRICS_SUB_NAMESPACE: String = "batch.inject.kafkaConsumer.metricsSubNamespace"
      val CONSUMER_RECONNECT_BACKOFF_MS_CONFIG: String = "batch.inject.kafkaConsumer.reconnectBackoffMsConfig"
      val CONSUMER_RECONNECT_BACKOFF_MAX_MS_CONFIG: String = "batch.inject.kafkaConsumer.reconnectBackoffMaxMsConfig"
      val PRODUCER_BOOTSTRAP_SERVERS: String = "batch.inject.kafkaProducer.bootstrapServers"
      val PRODUCER_TOPIC: String = "batch.inject.kafkaProducer.topic"
      val LINGER_MS: String = "batch.inject.kafkaProducer.lingerMS"
    }

    object Identity {
      val PRODUCER_BOOTSTRAP_SERVERS: String = "batch.identity.kafkaProducer.bootstrapServers"
      val PRODUCER_TOPIC: String = "batch.identity.kafkaProducer.topic"
      val LINGER_MS: String = "batch.identity.kafkaProducer.lingerMS"
    }
  }

}

object IdentityProducer extends ConfigBase {

  implicit val formats: Formats = Batch.formats

  val production: ExpressProducer[String, Identity] = new ExpressProducer[String, Identity] {
    val keySerializer: Serializer[String] = new StringSerializer
    val valueSerializer: Serializer[Identity] = (_: String, data: Identity) => {
      write(data).getBytes(StandardCharsets.UTF_8)
    }
    val producerBootstrapServers: String = conf.getString(Batch.Configs.Identity.PRODUCER_BOOTSTRAP_SERVERS)
    val lingerMs: Int = conf.getInt(Batch.Configs.Identity.LINGER_MS)
    val production: ProducerRunner[String, Identity] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
  }

  val producerTopic: String = conf.getString(Batch.Configs.Identity.PRODUCER_TOPIC)

}

object Elephant extends ExpressKafka[String, SessionBatchRequest, List[DeviceCreationState]] with WithShutdownHook with ConfigBase {
  override val keyDeserializer: Deserializer[String] = new StringDeserializer

  implicit val formats: Formats = Batch.formats
  override implicit val ec: ExecutionContext = Batch.executionContext
  override val valueDeserializer: Deserializer[SessionBatchRequest] = (_: String, data: Array[Byte]) => {
    read[SessionBatchRequest](new ByteArrayInputStream(data))
  }
  override val consumerTopics: Set[String] = conf.getString(Batch.Configs.Inject.CONSUMER_TOPIC).split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[SessionBatchRequest] = (_: String, data: SessionBatchRequest) => {
    write(data).getBytes(StandardCharsets.UTF_8)
  }
  override val consumerBootstrapServers: String = conf.getString(Batch.Configs.Inject.CONSUMER_BOOTSTRAP_SERVERS)
  override val consumerGroupId: String = conf.getString(Batch.Configs.Inject.CONSUMER_GROUP_ID)
  override val consumerMaxPollRecords: Int = conf.getInt(Batch.Configs.Inject.CONSUMER_MAX_POLL_RECORDS)
  override val consumerGracefulTimeout: Int = conf.getInt(Batch.Configs.Inject.CONSUMER_GRACEFUL_TIMEOUT)
  override val producerBootstrapServers: String = conf.getString(Batch.Configs.Inject.PRODUCER_BOOTSTRAP_SERVERS)
  override val metricsSubNamespace: String = conf.getString(Batch.Configs.Inject.METRICS_SUB_NAMESPACE)
  override val consumerReconnectBackoffMsConfig: Long = conf.getLong(Batch.Configs.Inject.CONSUMER_RECONNECT_BACKOFF_MS_CONFIG)
  override val consumerReconnectBackoffMaxMsConfig: Long = conf.getLong(Batch.Configs.Inject.CONSUMER_RECONNECT_BACKOFF_MAX_MS_CONFIG)
  override val lingerMs: Int = conf.getInt(Batch.Configs.Inject.LINGER_MS)
  override val maxTimeAggregationSeconds: Long = 120
  override val process: Process = Process.async { crs =>

    val result = crs
      .groupBy(x => x.value().session)
      .map { case (session, sessionBatchRequest) =>

        lazy val user = UserFactory.getByUsername(session.username)(session.realm)

        val batchRequests: List[Either[String, (Batch[_], BatchRequest)]] = sessionBatchRequest.toList
          .map(_.value().batchRequest)
          .map { br =>
            Batch
              .fromSymbol(br.batchType)
              .map(x => Right(x, br))
              .getOrElse(Left("Unknown batch_type"))
          }

        val devicesToAdd = batchRequests
          .map {
            _.flatMap {
              case (b, br) =>
                b.deviceFromBatchRequest(br)
                  .map { case (d, devices) =>
                    (b, d, devices)
                  }
            }
          }
          .flatMap {
            case Right((b, d, device)) =>
              List((b, d, device))
            case Left(value) =>
              logger.error("{}", value)
              Nil
          }

        devicesToAdd.map { case (b, d, _) =>
          b.storeCertificateInfo(d)
        }

        user.createMultipleDevicesAsync(devicesToAdd.map(_._3))

      }

    Future.sequence(result).map(_.toList.flatten)

  }
  val producerTopic: String = conf.getString(Batch.Configs.Inject.PRODUCER_TOPIC)

  override def prefix: String = "Ubirch"
}

case class SIMData(provider: String, imsi: String, pin: String, cert: String) {
  var id = ""
  def withId(newId: String): SIMData = {
    id = newId
    this
  }
}

case object SIM extends Batch[SIMData] with ConfigBase with StrictLogging {

  import Elephant.{ producerTopic, send }

  implicit val formats: Formats = Batch.formats

  override val value: Symbol = 'sim_import

  override val separator: String = conf.getString(Batch.Configs.FILE_SEPARATOR)

  override def storeCertificateInfo(cert: Any)(implicit ec: ExecutionContext): Future[Either[String, Boolean]] = cert match {
    case sim: SIMData =>
      IdentityProducer.production.send(IdentityProducer.producerTopic, Identity(sim.id, value.toString(), sim.cert))
        .map { _ =>
          Right(true)
        }.recover {
          case e: Exception =>
            logger.error(s"Error publishing to ${IdentityProducer.producerTopic} pipeline [${sim.toString}]", e)
            Left(s"Error publishing to identity pipeline [${sim.toString}]")
        }
    case _ => Future.successful(Left("Unknown data type received"))
  }

  override def deviceFromBatchRequest(batchRequest: BatchRequest): Either[String, (SIMData, AddDevice)] =
    for {
      simData <- buildSimData(batchRequest)
      id <- extractIdFromCert(simData.cert)
    } yield {
      (simData.withId(id), AddDevice(id, batchRequest.description, attributes = createAttributes(id, simData, batchRequest)))
    }

  //TODO: We need to get this uuid from the cert.
  private def extractIdFromCert(cert: String): Either[String, String] = Right(java.util.UUID.randomUUID().toString)

  private def createAttributes(id: String, simData: SIMData, batchRequest: BatchRequest): Map[String, List[String]] = {
    Map(
      "pin" -> List(simData.pin),
      "imsi" -> List(simData.imsi),
      "provider" -> List(simData.provider),
      "cert_id" -> List(id),
      "batch_type" -> List(batchRequest.batchType.name),
      "filename" -> List(batchRequest.filename),
      "tags" -> List(batchRequest.tags)
    )
  }

  private def buildSimData(batchRequest: BatchRequest): Either[String, SIMData] = {
    try {
      Right(Extraction.extract[SIMData](batchRequest.data))
    } catch {
      case e: Exception =>
        Left("Error parsing data: " + e.getMessage)
    }
  }

  override def ingest(fileItem: FileItem, skipHeader: Boolean, description: String, batchType: Symbol, tags: String)(implicit session: Session): ReadStatus = {
    val readStatus = Batch.read(fileItem.getInputStream, skipHeader) { line =>
      extractData(line, separator).map { d =>
        val jv = Extraction.decompose(d)
        val sbr = BatchRequest(fileItem.name, description, batchType, tags, jv).withSession
        send(producerTopic, sbr)
      }
    }

    readStatus

  }

  override def extractData(line: String, separator: String): Either[String, SIMData] = {
    line.split(separator).toList match {
      case List(provider, imsi, pin, cert) if provider.nonEmpty && imsi.nonEmpty && pin.nonEmpty && cert.nonEmpty =>
        Right(SIMData(provider, imsi, pin, cert))
      case _ =>
        logger.error("Error processing line [{}]", line)
        Left(s"Error processing line [$line]")
    }
  }

}

case class Identity(id: String, category: String, cert: String)

case class BatchRequest(filename: String, description: String, batchType: Symbol, tags: String, data: JValue) {
  def withSession(implicit session: Session): SessionBatchRequest = SessionBatchRequest(session, this)
}
case class Session(id: String, realm: String, username: String)
case class SessionBatchRequest(session: Session, batchRequest: BatchRequest)

