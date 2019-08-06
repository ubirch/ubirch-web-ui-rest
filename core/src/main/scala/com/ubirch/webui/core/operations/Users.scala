package com.ubirch.webui.core.operations

import com.ubirch.webui.core.Exceptions.UserNotFound
import com.ubirch.webui.core.structure.{Device, DeviceStubs, User}
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Devices._
import com.ubirch.webui.core.operations.Utils._
import scala.util.Try

import scala.collection.JavaConverters._

object Users {

  /*
 Return a FrontEndStruct.User element based on the KeyCloak username of the wanted user and the realm on which he belongs
  */
  def findUserByUsername(realmName: String, userName: String): User = {
    val userRepresentation = getKCUserFromUsername(realmName, userName).toRepresentation
    val lastName = Try(userRepresentation.getLastName).getOrElse("none")
    val firstName = Try(userRepresentation.getFirstName).getOrElse("none")
    val id = Try(userRepresentation.getId).getOrElse("none")
    User(id, userName, lastName, firstName)
  }

  /*
  Return a FrontEndStruct.User element based on the KeyCloak Id of the wanted user and the realm on which he belongs
   */
  def findUserById(realmName: String, userId: String): User = {
    val realm = getRealm(realmName)
    val userInternal = Option(realm.users().get(userId)) match {
      case Some(u) => u
      case None => throw UserNotFound(s"user with id $userId is not present in $realmName")
    }
    val userRepresentation = userInternal.toRepresentation
    val lastName = Try(userRepresentation.getLastName).getOrElse("none")
    val firstName = Try(userRepresentation.getFirstName).getOrElse("none")
    val username = Try(userRepresentation.getUsername).getOrElse("none")
    User(userId, username, lastName, firstName)
  }

  /*
 Same as listAllDevicesOfAUser, but for list of device stubs
  */
  def listAllDevicesStubsOfAUser(realmName: String, page: Int, pageSize: Int, userName: String): List[DeviceStubs] = {
    val devices: List[Device] = listAllDevicesOfAUser(realmName, page, pageSize, userName)
    devices map { d => DeviceStubs(d.hwDeviceId, d.description,  d.attributes.get("device type") match {
      case Some(value) => value.head
      case None => "none"
    })}
  }
  /*
  Find all the devices belonging to a user.
  The devices are stored in a group called "<username>_OWN_DEVICES".
  Find the group, then get the devices.
   */
  def listAllDevicesOfAUser(realmName: String, page: Int, pageSize: Int, userName: String): List[Device] = {
    val userInternal = getUserRepresentationFromUserName(realmName, userName)
    val userId = userInternal.getId
    val userGroups = getGroupsOfAUser(realmName, userId)
    val userDeviceGroup = userGroups.find{ g => g.name.contains("_OWN_DEVICES")} match {
      case Some(value) => value
      case None => createUserDeviceGroup(realmName, userInternal)
    }
    val userDeviceGroupId = userDeviceGroup.id
    val allUsersInGroup = findAllUsersInGroup(realmName, userDeviceGroupId)
    val allDevicesInGroup = allUsersInGroup.filter{ u => u.getAttributes.asScala.get("type") match {
      case Some(value) => value.contains("device")
      case None => false
    }}
    allDevicesInGroup map {device =>
      findDeviceByHwDevice(realmName, device.getUsername)
    }
  }

}
