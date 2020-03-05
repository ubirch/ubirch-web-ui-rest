package com.ubirch.webui.batch

import com.ubirch.webui.core.Exceptions.InternalApiException
import com.ubirch.webui.core.structure.BulkRequest
import com.ubirch.webui.core.structure.member.{ DeviceCreationFail, DeviceCreationState, DeviceCreationSuccess, UserFactory }


/**
 * Represents a simple Claiming abstraction
 */
sealed trait Claiming {

  def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState]

}

/**
 * Represents a SIM Claiming
 */
object SIMClaiming extends Claiming {

  override def claim(bulkRequest: BulkRequest)(implicit session: Session): List[DeviceCreationState] = {

    val user = UserFactory.getByUsername(session.username)(session.realm)

    bulkRequest.devices.map { device =>

      try {
        user.claimDevice(SIM.IMSI_PREFIX + device.secondaryIndex + SIM.IMSI_SUFFIX, bulkRequest.prefix.getOrElse(""), bulkRequest.tags, SIM.IMSI.name)
        DeviceCreationSuccess(device.secondaryIndex)
      } catch {
        case e: InternalApiException =>
          DeviceCreationFail(device.secondaryIndex, e.getMessage, e.errorCode)
        case e: Exception =>
          DeviceCreationFail(device.secondaryIndex, e.getMessage, -99)
      }

    }

  }

}

