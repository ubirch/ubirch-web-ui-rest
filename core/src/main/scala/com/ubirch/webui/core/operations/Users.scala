package com.ubirch.webui.core.operations

import com.ubirch.webui.core.Exceptions.InternalApiException
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{Device, DeviceStubs, Group, User}
import org.keycloak.representations.idm.UserRepresentation

import scala.util.Try

object Users {

  /*
 Return a FrontEndStruct.User element based on the KeyCloak username of the wanted user and the realm on which he belongs
  */
  def getUserByUsername(userName: String)(implicit realmName: String): User = {
    getMemberByUsername(userName, userRepresentationToUserStruct)
  }

  /*
  Return a FrontEndStruct.User element based on the KeyCloak Id of the wanted user and the realm on which he belongs
   */
  def getUserById(userId: String)(implicit realmName: String): User = {
    getMemberById(userId, userRepresentationToUserStruct)
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
    val userInternal = getKCUserFromUsername(userName).toRepresentation
    val userId = userInternal.getId

    Groups.getMembersInGroup[Device](getUserOwnDevicesGroup(userId).id, "DEVICE", completeDevice)

  }

  def getUserOwnDevicesGroup(userId: String)(implicit realmName: String): Group = {
    val userGroups = getAllGroupsOfAUser(userId)
    userGroups.find { g => g.name.contains("_OWN_DEVICES") } match {
      case Some(value) => value
      case None => throw new InternalApiException(s"User with Id $userId doesn't have a OWN_DEVICE group")
    }
  }

  def deleteUser(userId: String)(implicit realmName: String): Unit = {
    getRealm.users().get(userId).remove()
  }

  def userRepresentationToUserStruct(uRep: UserRepresentation): User = {
    val lastName = Try(uRep.getLastName).getOrElse("none")
    val firstName = Try(uRep.getFirstName).getOrElse("none")
    val username = Try(uRep.getUsername).getOrElse("none")
    val id = uRep.getId
    User(id, username, lastName, firstName)
  }

}
