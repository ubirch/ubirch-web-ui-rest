package com.ubirch.webui.batch

import java.io.{ BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader }
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security
import java.security.cert.X509Certificate
import java.util.concurrent.{ Executors, TimeUnit }
import java.util.{ Base64, UUID }

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.express.{ ExpressKafka, ExpressProducer, WithShutdownHook }
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.webui.core.structure.AddDevice
import com.ubirch.webui.core.structure.member._
import com.ubirch.webui.server.config.ConfigBase
import com.ubirch.webui.util.Hasher
import org.apache.commons.codec.binary.Hex
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.jce.PrincipalUtil
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.json4s.{ Formats, _ }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
  * Represents a Batch type
  * @tparam D Is the data that the batch processes or expects from the stream.
  */
sealed trait Batch[D] {
  val value: Symbol
  val separator: String

  def deviceAndDataFromBatchRequest(batchRequest: BatchRequest): Either[String, (DeviceEnabled[D], AddDevice)]

  def extractData(provider: String, line: String, separator: String): Either[String, D]

  def storeCertificateInfo(cert: Any)(implicit ec: ExecutionContext): Future[Either[String, Boolean]]

  def ingest(
      provider: String,
      streamName: String,
      inputStream: InputStream,
      skipHeader: Boolean,
      description: String,
      tags: String
  )(implicit session: Session): ResponseStatus

}

/**
  * Represents a helper object for the Batch type.
  */
object Batch extends StrictLogging with ConfigBase {

  private final val THREAD_POOL_SIZE: Int = conf.getInt(Batch.Configs.THREAD_POOL_SIZE)
  lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE))
  lazy val formats: Formats = Serialization.formats(NoTypeHints) ++ org.json4s.ext.JavaTypesSerializers.all
  val options: List[Batch[_]] = List(SIM)

  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[Batch[_]] = options.find(_.value.name == value)

  def fromSymbol(value: Symbol): Option[Batch[_]] = options.find(_.value == value)

  def read(inputStream: InputStream, skipHeader: Boolean)(f: String => Either[String, _]): ResponseStatus = {

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

        if (skipHeader && processed == 0) {
          it.next()
          processed = processed + 1
        } else {
          val value = it.next()
          if (value.nonEmpty) {
            f(value) match {
              case Right(_) => success = success + 1
              case Left(error) =>
                failure = failure + 1
                failureMessages += error
            }
            processed = processed + 1
          }
        }

      }

      if (skipHeader) processed = processed - 1

      ResponseStatus.Ok(processed, success, failure, failureMessages.toList)

    } catch {
      case e: Exception =>
        logger.error("Error processing stream [{}]", e.getMessage)
        ResponseStatus.Failure(processed, success, failure, failureMessages.toList)
    } finally {
      if (br != null) br.close()
      if (isr != null) isr.close()
    }

  }

  object Configs {
    val FILE_SEPARATOR: String = "batch.separator"
    val THREAD_POOL_SIZE: String = "batch.executionContext.threadPoolSize"

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

    object IdentityActivation {
      val PRODUCER_BOOTSTRAP_SERVERS: String = "batch.identityActivation.kafkaProducer.bootstrapServers"
      val PRODUCER_TOPIC: String = "batch.identityActivation.kafkaProducer.topic"
      val LINGER_MS: String = "batch.identityActivation.kafkaProducer.lingerMS"
    }

  }

  def buildUUID(uuid: String): Try[UUID] = {
    Try(UUID.fromString(uuid)).recover {
      case _: IllegalArgumentException =>
        val UUID_RADIX = 16
        val UUID_MIDDLE = 16
        new UUID(
          new BigInteger(uuid.substring(0, UUID_MIDDLE), UUID_RADIX).longValue(),
          new BigInteger(uuid.substring(UUID_MIDDLE), UUID_RADIX).longValue()
        )
    }
  }

}

/**
  * Represents the Express Kafka Producer that sends data to the Identity Service
  */
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

/**
  * Represents an Express Kafka implementation for the Ingest and Processing of the Import Stream
  */
object Elephant extends ExpressKafka[String, SessionBatchRequest, List[DeviceCreationState]] with WithShutdownHook with ConfigBase {

  implicit val formats: Formats = Batch.formats
  override implicit val ec: ExecutionContext = Batch.executionContext

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
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
  override val maxTimeAggregationSeconds: Long = 180

  override val process: Process = Process.async { crs =>

    val result = crs
      .groupBy(x => x.value().session)
      .flatMap {
        case (session, sessionBatchRequest) =>

          lazy val user = Suppliers.memoizeWithExpiration(new Supplier[User] {
            override def get(): User = UserFactory.getByUsername(session.username)(session.realm)
          }, 5, TimeUnit.MINUTES)

          val batchRequests: List[Either[String, (Batch[_], BatchRequest)]] = sessionBatchRequest.toList
            .map(_.value().batchRequest)
            .map { br =>
              Batch
                .fromSymbol(br.batchType)
                .map(x => Right(x, br))
                .getOrElse(Left("Unknown batch_type"))
            }

          batchRequests
            .map {
              _.flatMap {
                case (b, br) =>
                  b.deviceAndDataFromBatchRequest(br)
                    .map {
                      case (d, device) =>

                        user.get()
                          .createDeviceAdminAsync(device, d.provider)
                          .map { dc =>
                            if (dc.state == "ok") {
                              val stored = b.storeCertificateInfo(d.data)
                              stored.onComplete {
                                case Success(Right(true)) =>
                                case Success(Right(false)) =>
                                  logger.error("1. Not stored")
                                case Success(Left(value)) =>
                                  logger.error("2. Not stored {}", value)
                                case Failure(exception) =>
                                  logger.error("3. Not stored {}", exception.getMessage)
                              }
                              dc
                            } else {
                              dc
                            }
                          }
                    }
              }
            }.flatMap {
              case Right(fdc) =>
                val res = fdc.map {
                  case dcs @ DeviceCreationSuccess(_) => dcs
                  case dcf @ DeviceCreationFail(hwDeviceId, error, errorCode) =>
                    logger.error("1. Error processing: hwDeviceId={} message={} code={}", hwDeviceId, error, errorCode)
                    dcf.asInstanceOf[DeviceCreationState]
                }
                List(res)
              case Left(value) =>
                logger.error("Error processing: {}", value)
                Nil
            }

      }

    Future.sequence(result.toList)

  }

  val producerTopic: String = conf.getString(Batch.Configs.Inject.PRODUCER_TOPIC)

  override def prefix: String = "Ubirch"
}

/**
  * Represents a Batch for the SIM Cards.
  * The SIM Batch works with SIMData
  */
case object SIM extends Batch[SIMData] with ConfigBase with StrictLogging {

  final val MinIMSILength = 13
  final val MinPINLength = 4
  final val IMSI_PREFIX = "imsi_"
  final val IMSI_SUFFIX = "_imsi"
  final val PIN = 'pin
  final val IMSI = 'imsi
  final val PROVIDER = 'provider
  final val IDENTITY_ID = 'identity_id
  final val OWNER_ID = 'owner_id
  final val BATCH_TYPE = 'batch_type
  final val FILENAME = 'filename
  final val TAGS = 'import_tags

  val COMMONNAMEOID = new ASN1ObjectIdentifier("2.5.4.3")

  import Elephant.{ producerTopic, send }

  implicit val formats: Formats = Batch.formats

  override val value: Symbol = 'sim_import

  override val separator: String = conf.getString(Batch.Configs.FILE_SEPARATOR)

  override def storeCertificateInfo(cert: Any)(implicit ec: ExecutionContext): Future[Either[String, Boolean]] = cert match {
    case sim: SIMData =>
      val id = Identity(sim.certHash, sim.uuid, "X.509", sim.cert, value.name + "_" + sim.imsi)
      IdentityProducer.production.send(IdentityProducer.producerTopic, id)
        .map { _ =>
          Right(true)
        }.recover {
          case e: Exception =>
            logger.error(s"Error publishing to ${IdentityProducer.producerTopic} pipeline [${sim.toString}]", e)
            Left(s"Error publishing to identity pipeline [${sim.toString}]")
        }
    case _ => Future.successful(Left("Unknown data type received"))
  }

  def checkUUIDs(uuid1: String, uuid2: String): Either[String, String] = {

    val go = for {
      _normalizedUUID1 <- Try(uuid1.replaceAll("-", "").toLowerCase)
      _normalizedUUID2 <- Try(uuid2.replaceAll("-", "").toLowerCase)
      normalizedUUID1 <- Batch.buildUUID(_normalizedUUID1)
      normalizedUUID2 <- Batch.buildUUID(_normalizedUUID2) if normalizedUUID1 == normalizedUUID2
    } yield normalizedUUID1

    go.fold(x => Left("Error in IDs :=" + x.getMessage), u => Right(u.toString))

  }

  override def deviceAndDataFromBatchRequest(batchRequest: BatchRequest): Either[String, (DeviceEnabled[SIMData], AddDevice)] = {

    val dataToProcess = for {
      simData <- buildSimData(batchRequest)
      updatedSimData <- extractIdFromCert(simData.cert)
        .flatMap(x => checkUUIDs(x, simData.uuid))
        .map(x => simData.withUUID(x))
    } yield (updatedSimData, AddDevice(
      updatedSimData.uuid,
      secondaryIndex = updatedSimData.imsi,
      description = batchRequest.description,
      attributes = createAttributes(updatedSimData, batchRequest)
    ))

    processingVerification(dataToProcess)

  }

  private[batch] def extractIdFromCert(cert: String): Either[String, String] = {

    def extract(encoding: CertEncoding) = {
      if (cert.nonEmpty) {
        try {

          val certBin = encoding match {
            case Base64Encoded => Base64.getDecoder.decode(cert)
            case HexEncoded => Hex.decodeHex(cert)
          }

          val factory = security.cert.CertificateFactory.getInstance("X.509")
          if (factory != null) {
            try {

              val x509Cert = factory.generateCertificate(new ByteArrayInputStream(certBin)).asInstanceOf[X509Certificate]
              val principal = PrincipalUtil.getSubjectX509Principal(x509Cert)
              val values = principal.getValues(COMMONNAMEOID)
              if (values.size() == 1) {
                val cn = values.get(0).asInstanceOf[String]
                Right(cn)
              } else
                Left(s"Got invalid cert subject, missing common name: $cert")
            } catch {
              case e: Exception =>
                logger.error("Error processing cert (1) -> {}", e.getMessage)
                Left(s"Got invalid cert binary data: $cert")
            }
          } else
            Left("Error while initiating X.509 Factory")
        } catch {
          case e: Exception =>
            logger.error("Error processing cert (2) -> {} ", e.getMessage)
            Left(s"Got invalid cert string: $cert")
        }
      } else
        Left("Got an empty cert")
    }

    logger.info(s"Attempting extraction with encoding=${HexEncoded.toString}")
    extract(HexEncoded) match {
      case Left(_) =>
        logger.info(s"Attempting extraction with encoding=${Base64Encoded.toString}")
        extract(Base64Encoded)
      case r @ Right(_) => r
    }

  }

  private[batch] def createAttributes(simData: SIMData, batchRequest: BatchRequest): Map[String, List[String]] = {
    Map(
      PIN.name -> List(simData.pin),
      IMSI.name -> List(simData.imsi),
      PROVIDER.name -> List(simData.provider),
      OWNER_ID.name -> List(simData.uuid),
      IDENTITY_ID.name -> List(Hasher.hash(simData.cert)),
      BATCH_TYPE.name -> List(batchRequest.batchType.name),
      FILENAME.name -> List(batchRequest.filename),
      TAGS.name -> List(batchRequest.tags)
    )
  }

  private[batch] def buildSimData(batchRequest: BatchRequest): Either[String, SIMData] = {
    try {
      Right(Extraction.extract[SIMData](batchRequest.data))
    } catch {
      case e: Exception =>
        Left("Error parsing data: " + e.getMessage)
    }
  }

  override def ingest(
      provider: String,
      streamName: String,
      inputStream: InputStream,
      skipHeader: Boolean,
      description: String,
      tags: String
  )(implicit session: Session): ResponseStatus = {
    Batch.read(inputStream, skipHeader) { line =>
      extractData(provider, line, separator).map { d =>
        val jv = Extraction.decompose(d)
        val sbr = BatchRequest(streamName, description, value, tags, jv).withSession
        send(producerTopic, sbr)
      }
    }
  }

  def extractionVerification(line: String)(data: Either[String, SIMData]): Either[String, SIMData] = {
    val newUUID = for {
      simData <- data
      oldUUID = simData.uuid
      _newUUID <- Batch.buildUUID(oldUUID).fold(e => Left(e.getMessage), u => Right(u))
    } yield _newUUID.toString

    val certId = for {
      simData <- data
      extractedUUIDAsString <- extractIdFromCert(simData.cert)
      cId <- Batch.buildUUID(extractedUUIDAsString).fold(e => Left(e.getMessage), u => Right(u))
    } yield cId.toString

    val certIdCheck = for {
      uuidFromData <- newUUID
      uuidFromCert <- certId
    } yield uuidFromCert == uuidFromData

    data match {
      case Right(d) if d.uuid.isEmpty => Left(s"UUID cannot be empty @ Line [$line]")
      case Right(d) if d.provider.isEmpty => Left(s"Provider cannot be empty @ Line [$line]")
      case Right(d) if d.imsi.isEmpty || d.imsi.length < MinIMSILength => Left(s"IMSI is invalid [${d.imsi}], min length=$MinIMSILength @ Line [$line]")
      case Right(d) if d.pin.isEmpty || d.pin.length < MinPINLength => Left(s"Pin is invalid [${d.pin}],  min length=$MinPINLength @ Line [$line]")
      case Right(d) if newUUID.isLeft => Left(s"UUID is invalid oldUUID=[${d.uuid}] @ Line [$line]")
      case Right(_) if certId.isLeft => Left(s"Cert is invalid @ Line [$line]")
      case Right(d) if !certIdCheck.contains(true) => Left(s"UUID doesn't match Cert Id UUID=[${newUUID.getOrElse(d.uuid)}] certId=[${certId.getOrElse("--")}] @ Line [$line]")
      case Right(d) =>
        newUUID.map { x =>
          d.withUUID(x)
            .withIMSIPrefixAndSuffix(SIM.IMSI_PREFIX, SIM.IMSI_SUFFIX)
        }
      case left @ Left(_) =>
        logger.error("Error processing line [{}]", line)
        left
    }
  }

  def processingVerification(data: Either[String, (SIMData, AddDevice)]): Either[String, (DeviceEnabled[SIMData], AddDevice)] = {
    data match {
      case Right((d, _)) if d.uuid.isEmpty => Left(s"Error processing :=[${d.toString}]: UUID cannot be empty")
      case Right((d, _)) if d.provider.isEmpty => Left(s"Error processing := [${d.toString}]: Provider cannot be empty")
      case Right((d, _)) if d.imsi.isEmpty || d.imsi.length < MinIMSILength => Left(s"IMSI is invalid [${d.imsi}], min length=$MinIMSILength @ [${d.toString}]")
      case Right((d, _)) if d.pin.isEmpty || d.pin.length < MinPINLength => Left(s"Pin is invalid [${d.pin}], min length=$MinPINLength @ [${d.toString}]")
      case Right((_, div)) if div.hwDeviceId.isEmpty => Left("hwDeviceId can't be empty")
      case Right((d, div)) if checkUUIDs(d.uuid, div.hwDeviceId).isLeft => Left(s"The uuid extracted from the cert is not the same as the received data. uuid=${d.uuid} hwDeviceId=${div.hwDeviceId}")
      case Right((d, div)) => Right(DeviceEnabled(d.provider, d), div)
      case Left(value) => Left(value)
    }
  }

  override def extractData(provider: String, line: String, separator: String): Either[String, SIMData] = {
    extractionVerification(line) {
      line.split(separator).toList match {
        case List(imsi, pin, uuid, cert) =>
          Right(SIMData(provider, imsi, pin, uuid, cert))
        case _ =>
          Left(s"Error extracting line [$line], hint: separator=[$separator]")
      }
    }
  }

}

/**
  * Represents a wrapper type for being able to address the provider from within the processing pipiline
  * @param provider Represents the provider
  * @param data Represents the data that is wrapped
  * @tparam D Represents the type of the data that is wrapped
  */

case class DeviceEnabled[D](provider: String, data: D)

/***
 * Represents the type of data that the batch SIM will handle
 * @param provider Represents the entity or person that provides the data
 * @param imsi Represents the IMSI for the card
 * @param pin Represents the PIN for the SIM Card
 * @param cert Represents the base64-encoded X.509 certificate
 */

case class SIMData(provider: String, imsi: String, pin: String, uuid: String, cert: String) {
  def certHash = Hasher.hash(cert)
  def withUUID(newUUID: String): SIMData = copy(uuid = newUUID)
  def withIMSIPrefixAndSuffix(prefix: String, suffix: String): SIMData = {
    if (imsi.startsWith(prefix) && imsi.endsWith(suffix)) this
    else copy(imsi = prefix + imsi + suffix)
  }
}

/**
  * Represents the type that will be returned after having ingested the data.
  * @param status Represents the success or not of the injections
  * @param accepted Represents the number of records received by the consumer.
  * @param success Represents the number of successes by injection
  * @param failure Represents the number of failures for the injection process
  * @param failures Represents a list of messages of errors.
  */
case class ResponseStatus(status: Boolean, accepted: Int, success: Int, failure: Int, failures: List[String])

/**
  * Represents a companion object for the ResponseStatus response object.
  * It offers an easy way to create Successes or Failure Responses.
  */
object ResponseStatus {
  def Ok(processed: Int, success: Int, failure: Int, failures: List[String]) =
    ResponseStatus(status = true, processed, success, failure, failures)

  def Failure(processed: Int, success: Int, failure: Int, failures: List[String]) =
    ResponseStatus(status = false, processed, success, failure, failures)

}

/**
  * Represents an Identity
  *
  * @param id Represents a unique identifier
  * @param category Represents the category for the identity
  * @param data Represents the raw certificate X.509. or CSR It is usually sent as Hex or Base64
  */

case class Identity(id: String, ownerId: String, category: String, data: String, description: String)

/**
  * Represents an Identity Activation
  *
  * @param identityId Represents a unique identifier
  * @param ownerId Represents the owner of the identity
  */

case class IdentityActivation(identityId: String, ownerId: String) {
  def validate: Boolean = identityId.nonEmpty && ownerId.nonEmpty
}

/**
  * Represents a Requests for Processing.
  * When the data is read from the stream, a batch request is created. The Batch request contains all the information that is needed
  * for the processing phase.
  * At the Keycloak level, some of these values are stored are Keycloak attributes
  * @param filename Represents the name of the stream or file from where the data is coming
  * @param description Represents the description of the file or stream.
  * @param batchType Represents the type of Batch import
  * @param tags Represents a list (comma-separated) of tags for the batch.
  * @param data Represents the data to be processed.
  */

case class BatchRequest(filename: String, description: String, batchType: Symbol, tags: String, data: JValue) {
  def withSession(implicit session: Session): SessionBatchRequest = SessionBatchRequest(session, this)
}

/**
  * Represents the information of the person uploading or injecting the data
  * @param id Represents the id of the user creating the request.
  * @param realm Represents the realm to which the person belongs.
  * @param username Represents the username of the person uploading the data.
  */
case class Session(id: String, realm: String, username: String)

/**
  * Represents a Session (+) a BatchRequest.
  * @param session Represents the session for the injection
  * @param batchRequest Represents the request that contains all the needed information for the processing.
  */
case class SessionBatchRequest(session: Session, batchRequest: BatchRequest)

/**
  * Represents the kind of encoding available for the incoming cert
  */
sealed trait CertEncoding

/***
 * Represents the base64 encoding type
 */
case object Base64Encoded extends CertEncoding

/**
  * Represents the Hex encoding type
  */
case object HexEncoded extends CertEncoding
