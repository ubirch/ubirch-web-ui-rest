package com.ubirch.webui.core.operations

import com.ubirch.webui.core.Exceptions.DeviceNotFound
import com.ubirch.webui.core.operations.Utils.{completeDevice, getRealm}
import com.ubirch.webui.core.structure.{Device, DeviceStubs}
import org.keycloak.representations.idm.UserRepresentation

object Devices {

  /*
  Return a FrontEndStruct.Device element based on the deviceHwId of the device (its keycloak username)
   */
  def findDeviceByHwDevice(realmName: String, deviceHwId: String): Device = {
    val realm = getRealm(realmName)
    val deviceDb: UserRepresentation = realm.users().search(deviceHwId, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with id $deviceHwId is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb, realmName)
  }

  /*
  Return a FrontEndStruct.Device element based on the description of the device (its keycloak last name)
 */
  def findDeviceByDescription(realmName: String, description: String): Device = {
    val realm = getRealm(realmName)
    val deviceDb = realm.users().search(description, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with description $description is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb, realmName)
  }


}
