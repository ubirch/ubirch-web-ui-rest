package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.DeviceNotFound
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Users._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.Device
import javax.ws.rs.core.Response
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}

import scala.collection.JavaConverters._

object Devices {


  /**
    * Create de device, returns the KC ID of the device.
    *
    * @param ownerId     KeyCloak id of the owner of the device.
    * @param hwDeviceId  External id of the device.
    * @param description Description of the device, which will become its last name.
    * @param deviceType  Type of the device. Delaut = "default_type".
    * @param groupsId    List of KeyCloak group ID that the device will join.
    * @param realmName   Name of the realm.
    * @return Id of the newly created device.
    */
  def createDevice(ownerId: String, hwDeviceId: String, description: String = "", deviceType: String, groupsId: List[String])(implicit realmName: String): String = {
    val realm = getRealm
    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setUsername(hwDeviceId)
    if (!description.equals("")) {
      deviceRepresentation.setLastName(description)
    } else deviceRepresentation.setLastName(hwDeviceId)

    // get groups of the user and find the ApiConfigGroup
    val groups = getGroupsOfAUser(ownerId)
    val apiConfigGroupId = groups find { g => g.name.equals(realmName + "_apiConfigGroup_default") } match {
      case Some(v) => v.id
      case None => throw new Exception(s"No ApiConfigGroup available in this realm")
    }
    val apiConfigGroupAttributes = realm.groups().group(apiConfigGroupId).toRepresentation.getAttributes.asScala.toMap

    // get DeviceConfigurationGroup
    val allGroups = realm.groups().groups().asScala.toList
    val deviceConfigGroup = allGroups.find { g => g.getName.equals(s"${deviceType}_DeviceConfigGroup") } match {
      case Some(v) => v
      case None => throw new Exception(s"No ApiConfigGroup available for device type $deviceType")
    }
    val deviceConfigGroupAttributes = realm.groups().group(deviceConfigGroup.getId).toRepresentation.getAttributes.asScala.toMap

    // set attributes and credentials
    setCredential(deviceRepresentation, apiConfigGroupAttributes)
    val allAttributes = (apiConfigGroupAttributes ++ deviceConfigGroupAttributes).asJava
    deviceRepresentation.setAttributes(allAttributes)

    // create device in KC
    val res: Response = realm.users().create(deviceRepresentation)
    val deviceKcId = ApiUtil.getCreatedId(res)
    val deviceKc = getKCUserFromId(deviceKcId)
    // set role DEVICE
    val roleDevice = realm.roles().get("DEVICE").toRepresentation
    addRoleToUser(deviceKc, roleDevice)

    // join groups
    if (groupsId.nonEmpty) groupsId foreach { groupId => addSingleUserToGroup(groupId, deviceKcId) }
    addSingleUserToGroup(apiConfigGroupId, deviceKcId)
    addSingleUserToGroup(deviceConfigGroup.getId, deviceKcId)
    val userOwnDeviceGroup = getUserOwnDevicesGroup(ownerId)
    deviceKc.joinGroup(userOwnDeviceGroup.id)
    deviceKcId
  }

  def bulkCreateDevice(ownerId: String, devicesConf: List[(String, String, String, List[String])])(implicit realmName: String): List[String] = {
    devicesConf map { d =>
      try {
        createDevice(ownerId, d._1, d._2, d._3, d._4)
        "OK " + d._1
      } catch {
        case e: Exception => e.getMessage + d._1
      }
    }
  }

  private def setCredential(deviceRepresentation: UserRepresentation, apiConfigGroupAttributes: Map[String, java.util.List[String]]): Unit = {
    val pwdDevice = apiConfigGroupAttributes.get("default_password") match {
      case Some(pwd) => pwd.get(0)
      case None => throw new Exception(s"No default password for ApiConfigGroup of the realm")
    }

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(pwdDevice)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))
  }

  /*
  Return a FrontEndStruct.Device element based on the deviceHwId of the device (its keycloak username)
   */
  def findDeviceByHwDevice(deviceHwId: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val deviceDb: UserRepresentation = realm.users().search(deviceHwId, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with id $deviceHwId is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb)
  }

  /*
  Return a FrontEndStruct.Device element based on the description of the device (its keycloak last name)
 */
  def findDeviceByDescription(description: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val deviceDb = realm.users().search(description, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with description $description is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb)
  }

  def findDeviceByInternalKcId(internalKCId: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val device = realm.users().get(internalKCId)
    completeDevice(device.toRepresentation)
  }

  def deleteDevice(deviceId: String)(implicit realmName: String): Unit = deleteUser(deviceId)

}
