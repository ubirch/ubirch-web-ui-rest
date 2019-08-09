package com.ubirch.webui.core.operations

import com.ubirch.webui.core.Exceptions.UserNotFound
import com.ubirch.webui.core.operations.Devices._
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{Device, DeviceStubs, Group, User}
import org.keycloak.admin.client.resource.UserResource

import scala.util.Try

object Users {

  /*
 Return a FrontEndStruct.User element based on the KeyCloak username of the wanted user and the realm on which he belongs
  */
  def findUserByUsername(userName: String)(implicit realmName: String): User = {
    val userRepresentation = getKCUserFromUsername(realmName, userName).toRepresentation
    val lastName = Try(userRepresentation.getLastName).getOrElse("none")
    val firstName = Try(userRepresentation.getFirstName).getOrElse("none")
    val id = Try(userRepresentation.getId).getOrElse("none")
    User(id, userName, lastName, firstName)
  }

  /*
  Return a FrontEndStruct.User element based on the KeyCloak Id of the wanted user and the realm on which he belongs
   */
  def findUserById(userId: String)(implicit realmName: String): User = {
    val realm = getRealm(realmName)
    val userInternal: UserResource = Option(realm.users().get(userId)) match {
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
  def listAllDevicesStubsOfAUser(page: Int, pageSize: Int, userName: String)(implicit realmName: String): List[DeviceStubs] = {
    val devices: List[Device] = listAllDevicesOfAUser(page, pageSize, userName)
    devices map { d => DeviceStubs(d.hwDeviceId, d.description, d.deviceType) }
  }
  /*
  Find all the devices belonging to a user.
  The devices are stored in a group called "<username>_OWN_DEVICES".
  Find the group, then get the devices.
   */
  def listAllDevicesOfAUser(page: Int, pageSize: Int, userName: String)(implicit realmName: String): List[Device] = {
    val userInternal = getUserRepresentationFromUserName(realmName, userName)
    val userId = userInternal.getId
    val userGroups = getAllGroupsOfAUser(userId)
    val userDeviceGroup = userGroups.find{ g => g.name.contains("_OWN_DEVICES")} match {
      case Some(value) => value
      case None => throw new Exception(s"User $userName doesn't have a device group")
    }
    val userDeviceGroupId = userDeviceGroup.id
    val allUsersInGroup = findAllUsersInGroup(realmName, userDeviceGroupId)
    val allDevicesInGroup = allUsersInGroup.filter { u =>
      isOfType(u.getId, "DEVICE")
    }
    allDevicesInGroup map {device =>
      findDeviceByHwDevice(device.getUsername)
    }
  }

  def getUserOwnDevicesGroup(userId: String)(implicit realmName: String): Group = {
    val userGroups = getAllGroupsOfAUser(userId)
    userGroups.find { g => g.name.contains("_OWN_DEVICES") } match {
      case Some(value) => value
      case None => throw new Exception(s"User with Id $userId doesn't have a OWN_DEVICE group")
    }
  }

  def deleteUser(userId: String)(implicit realmName: String): Unit = {
    getRealm.users().get(userId).remove()
  }

}
