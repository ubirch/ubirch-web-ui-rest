package com.ubirch.webui.batch

import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.kafka.GenericProducer
import com.ubirch.webui.models.Exceptions.{ AttributesNotFound, InternalApiException }
import com.ubirch.webui.models.keycloak.BulkRequest
import com.ubirch.webui.models.keycloak.member._
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.util.{ QuickActions, Util }
import org.json4s.Formats
import org.json4s.jackson.Serialization._

/**
  * Represents a simple Claiming abstraction
  */
sealed trait Claiming { def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState] }

/**
  * Represents a SIM Claiming
  */
object SIMClaiming extends Claiming with LazyLogging {

  implicit val formats: Formats = Batch.formats

  override def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState] = {

    val user = QuickActions.quickSearchUserNameOnlyOne(session.username)(session.realm).toResourceRepresentation(session.realm)

    bulkRequest.devices.map { device =>

      try {

        val deviceToClaim = DeviceFactory.getBySecondaryIndex(device.secondaryIndex, "imsi")(session.realm)
        val attributes = Util.attributesToMap(deviceToClaim.getAttributes)

        val maybeClaim = for {
          identityId <- attributes.get(SIM.IDENTITY_ID.name).flatMap(_.headOption)
          ownerId <- attributes.get(SIM.OWNER_ID.name).flatMap(_.headOption)
          dataHash <- attributes.get(SIM.DATA_HASH.name).flatMap(_.headOption)
        } yield {
          //we fire and forget
          GenericProducer.send(
            GenericProducer.ACTIVATION_PRODUCER_TOPIC,
            write(IdentityActivation(ownerId, identityId, dataHash)).getBytes(StandardCharsets.UTF_8)
          )
          user.claimDevice(SIM.IMSI_PREFIX + device.secondaryIndex + SIM.IMSI_SUFFIX, bulkRequest.prefix.getOrElse(""), bulkRequest.tags, SIM.IMSI.name, device.description)
        }

        maybeClaim
          .map(_ => DeviceCreationSuccess(device.secondaryIndex))
          .getOrElse(throw AttributesNotFound("No attributes found for claiming process"))

      } catch {
        case e: AttributesNotFound =>
          logger.error(s"Error when claiming device (1): ${e.getMessage}", e)
          DeviceCreationFail(device.secondaryIndex, e.getMessage, e.errorCode)
        case e: InternalApiException =>
          logger.error(s"Error when claiming device (2): ${e.getMessage}", e)
          DeviceCreationFail(device.secondaryIndex, e.getMessage, e.errorCode)
        case e: Exception =>
          logger.error(s"Error when claiming device (3):${e.getMessage}", e)
          DeviceCreationFail(device.secondaryIndex, e.getMessage, -99)
      }

    }

  }

}

