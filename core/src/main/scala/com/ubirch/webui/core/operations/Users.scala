package com.ubirch.webui.core.operations

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.InternalApiException
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure._
import org.keycloak.representations.idm.UserRepresentation

import scala.util.Try

object Users extends LazyLogging {

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
  def listAllDevicesStubsOfAUser(page: Int, pageSize: Int, userName: String)(implicit realmName: String): (List[DeviceStubs], Int) = {
    val (devices, sizeTotalDevices) = listAllDevicesOfAUser(page, pageSize, userName)
    (devices map { d => DeviceStubs(d.hwDeviceId, d.description, d.deviceType) }, sizeTotalDevices)
  }

  /*
  Find all the devices belonging to a user.
  The devices are stored in a group called "<username>_OWN_DEVICES".
  Find the group, then get the devices.
   */
  def listAllDevicesOfAUser(page: Int, pageSize: Int, userName: String)(implicit realmName: String): (List[Device], Int) = {
    val userInternal = getKCUserFromUsername(userName).toRepresentation
    val userId = userInternal.getId

    Groups.getMembersInGroup[Device](getUserOwnDevicesGroup(userId).id, Elements.DEVICE, completeDevice, page, pageSize)

  }

  def getUserOwnDevicesGroup(userId: String)(implicit realmName: String): Group = {
    val userGroups = getAllGroupsOfAUser(userId)
    userGroups.find { g => g.name.contains(Elements.PREFIX_OWN_DEVICES) } match {
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

  /**
    * Check if user has the role user
    * @param userId    Id of the user
    * @param realmName realm where the user is.
    * @return Boolean
    */
  def doesUserHasUserRole(userId: String)(implicit realmName: String): Boolean = {
    val userRoles = Utils.getMemberRoles(userId)
    if (userRoles.contains(Elements.DEVICE)) throw new InternalApiException("user is a device OR also has the role device")
    userRoles.contains(Elements.USER)
  }

  def doesUserHasGroup(userId: String)(implicit realmName: String): Boolean = {
    try {
      getUserOwnDevicesGroup(userId)
      true
    } catch {
      case _: Throwable => false
    }
  }

  def fullyCreateUser(userId: String)(implicit realmName: String): (Boolean, Boolean) = {
    val realm = getRealm
    val userHasRole = if (!doesUserHasUserRole(userId)) {
      addRoleToUser(getKCUserFromId(userId), realm.roles().get(Elements.USER).toRepresentation)
      logger.debug(s"added role USER to user with id $userId")
      true
    } else false
    val userHasDeviceGroup = if (!doesUserHasGroup(userId)) {
      Groups.createUserDeviceGroup(Utils.getKCUserFromId(userId).toRepresentation)
      logger.debug(s"added group OWN_DEVICES to user val id $userId")
      true
    } else false
    (userHasRole, userHasDeviceGroup)
  }

  def getAccountInfo(userId: String)(implicit realmName: String): (User ,Int) = {
    fullyCreateUser(userId)
    val userOwnDevicesGroup = getUserOwnDevicesGroup(userId)
    val group = Utils.getKCGroupFromId(userOwnDevicesGroup.id)
    val numberDevices = group.members().size() - 1
    val user = getUserById(userId)
    (user, numberDevices)
  }

}
