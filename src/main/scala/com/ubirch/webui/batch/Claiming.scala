package com.ubirch.webui.batch

import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.Exceptions.{AttributesNotFound, InternalApiException}
import com.ubirch.webui.models.keycloak.member._
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.BulkRequest
import com.ubirch.webui.models.keycloak.util.{Converter, QuickActions}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.json4s.Formats
import org.json4s.jackson.Serialization._

/**
  * Represents a simple Claiming abstraction
  */
sealed trait Claiming {

  def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState]

}

/**
  * Represents the Express Kafka Producer that sends data to the Identity Service
  */
object IdentityActivationProducer extends ConfigBase {

  implicit val formats: Formats = Batch.formats

  val production: ExpressProducer[String, IdentityActivation] = new ExpressProducer[String, IdentityActivation] {
    val keySerializer: Serializer[String] = new StringSerializer
    val valueSerializer: Serializer[IdentityActivation] = (_: String, data: IdentityActivation) => {
      write(data).getBytes(StandardCharsets.UTF_8)
    }
    val producerBootstrapServers: String = conf.getString(Batch.Configs.IdentityActivation.PRODUCER_BOOTSTRAP_SERVERS)
    val lingerMs: Int = conf.getInt(Batch.Configs.IdentityActivation.LINGER_MS)
    val production: ProducerRunner[String, IdentityActivation] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
  }

  val producerTopic: String = conf.getString(Batch.Configs.IdentityActivation.PRODUCER_TOPIC)

}

/**
  * Represents a SIM Claiming
  */
object SIMClaiming extends Claiming with LazyLogging {

  override def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState] = {

    val user = QuickActions.quickSearchUserNameOnlyOne(session.username)(session.realm).toResourceRepresentation(session.realm)

    bulkRequest.devices.map { device =>

      try {

        val deviceToClaim = DeviceFactory.getBySecondaryIndex(device.secondaryIndex, "imsi")(session.realm)
        val attributes = Converter.attributesToMap(deviceToClaim.getAttributes)

        val maybeClaim = for {
          identityId <- attributes.get(SIM.IDENTITY_ID.name).flatMap(_.headOption)
          ownerId <- attributes.get(SIM.OWNER_ID.name).flatMap(_.headOption)
          dataHash <- attributes.get(SIM.DATA_HASH.name).flatMap(_.headOption)
        } yield {
          //we fire and forget
          IdentityActivationProducer.production.send(
            IdentityActivationProducer.producerTopic,
            IdentityActivation(ownerId, identityId, dataHash)
          )
          user.claimDevice(SIM.IMSI_PREFIX + device.secondaryIndex + SIM.IMSI_SUFFIX, bulkRequest.prefix.getOrElse(""), bulkRequest.tags, SIM.IMSI.name)
        }

        maybeClaim
          .map(_ => DeviceCreationSuccess(device.secondaryIndex))
          .getOrElse(throw AttributesNotFound("No attributes found for claiming process"))

      } catch {
        case e: AttributesNotFound =>
          DeviceCreationFail(device.secondaryIndex, e.getMessage, e.errorCode)
        case e: InternalApiException =>
          DeviceCreationFail(device.secondaryIndex, e.getMessage, e.errorCode)
        case e: Exception =>
          logger.error("Error when claiming: ", e)
          DeviceCreationFail(device.secondaryIndex, e.getMessage, -99)
      }

    }

  }

}

