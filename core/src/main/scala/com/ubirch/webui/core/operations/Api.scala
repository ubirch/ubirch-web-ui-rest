package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{DeviceNotFound, GroupNotFound, UserNotFound}
import com.ubirch.webui.core.connector.KeyCloakConnector
import com.ubirch.webui.core.structure._
import org.keycloak.admin.client.resource.{GroupResource, RealmResource, UserResource}
import org.keycloak.representations.idm.{GroupRepresentation, UserRepresentation}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class Api {

  def createGroup(realmName: String, name: String): Group = {
    val realm = getRealm(realmName)
    val groupStructInternal = new GroupRepresentation
    groupStructInternal.setName(name)
    val res = realm.groups().add(groupStructInternal)
    val idGroup = ApiUtil.getCreatedId(res)
    Group(idGroup, name)
  }

  def findGroupByName(realmName: String, name: String): Group = {
    val realm = getRealm(realmName)
    val res = Try(realm.groups().groups(name, 0, 1))
    if (res.isFailure) {
      throw GroupNotFound(s"group with name $name is not present in $realmName")
    } else {
      val groupDb = res.get.get(0)
      Group(groupDb.getId, name)
    }
  }

  def findGroupById(realmName: String, groupId: String): Group = {
    val realm = getRealm(realmName)
    val res = Try(realm.groups().group(groupId))
    if (res.isFailure) {
      throw GroupNotFound(s"group with name $groupId is not present in $realmName")
    } else {
      val groupDb = res.get
      Group(groupId, groupDb.toRepresentation.getName)
    }
  }


  /*
  Return a FrontEndStruct.User element based on the KeyCloak username of the wanted user and the realm on which he belongs
   */
  def findUserByUsername(realmName: String, userName: String): User = {
    val realm = getRealm(realmName)
    val userInternalOption = realm.users().search(userName).asScala.headOption match {
      case Some(value) => value
      case None => throw UserNotFound(s"user with username $userName is not present in $realmName")
    }
    val lastName = Try(userInternalOption.getLastName).getOrElse("none")
    val firstName = Try(userInternalOption.getFirstName).getOrElse("none")
    val id = Try(userInternalOption.getId).getOrElse("none")
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
    completeGroup(deviceDb, realmName)
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
    completeGroup(deviceDb, realmName)
  }

  /*
  Create a device group for a user named "<username>_OWN_DEVICES"
  Every user has one
   */
  def createUserDeviceGroup(realmName: String, userInternal: UserRepresentation): Group = {
    val nameOfGroup = s"{${userInternal.getUsername}}_OWN_DEVICES"
    val group = createGroup(realmName, nameOfGroup)
    addSingleUserToGroup(realmName, group.id, userInternal.getId)
    group
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

  /*
  Add a device to a group from a user
   */
  def addDeviceFromUserToGroup(realmName: String, userId: String, deviceId: String, groupId: String): Boolean = {
    if (!canUserAddDeviceToGroup(realmName, userId, deviceId, groupId))
      throw new Exception("User cannot add device to group") else {
      try {
        val device = getRealm(realmName).users().get(deviceId)
        device.joinGroup(groupId)
        true
      } catch {
        case e: Exception =>
          throw e
          false
      }
    }
  }


  /*
  Get all the groups a user is subscribed to
   */
  def getGroupsOfAUser(realmName: String, userId: String): List[Group] = {
    val realm = getRealm(realmName)
    val groupsDb: mutable.Seq[GroupRepresentation] = Option(realm.users().get(userId).groups().asScala) match {
      case Some(value) => value
      case None =>
        throw DeviceNotFound(s"user/device with id $userId is not present in $realmName")
    }
    groupsDb.map{g => Group(g.getId, g.getName)}.toList
  }

  /*
  Return a keycloak instance belonging to a realm
   */
  private def getRealm(implicit realmName: String): RealmResource = {
    KeyCloakConnector.get.kc.realm(realmName)
  }

  /*
  Get a user representation from its userName and the realm he belongs to.
  Assumes that (username, realm) is a primary key (unique).
   */
  private def getUserRepresentationFromUserName(realmName: String, userName: String): UserRepresentation = {
    val realm = getRealm(realmName)
    val userInternalOption = realm.users().search(userName).asScala.headOption match {
      case Some(value) => value
      case None => throw UserNotFound(s"user with username $userName is not present in $realmName")
    }
    userInternalOption
  }

    /*
  Get all users in a group
   */
  private def findAllUsersInGroup(realmName: String, groupId: String): List[UserRepresentation] = {
    val realm = getRealm(realmName)
    realm.groups().group(groupId).members().asScala.toList
  }

  /*
  Return a full FrontEndStruct.Device representation based on its KeyCloack.UserRepresentation object
 */
  private def completeGroup(device: UserRepresentation, realmName: String): Device = {
    val deviceHwId = device.getUsername
    val deviceInternalId = device.getId
    val description = device.getLastName
    val lGroups = getGroupsOfAUser(realmName, deviceInternalId)
    val attributes = device.getAttributes.asScala.toMap map { x => x._1 -> x._2.asScala.toList}
    Device(deviceHwId,
      description,
      owner = null, //TODO: fix that once I figure how to
      groups = lGroups,
      attributes)
  }

  /*
  Add a user (device or normal user) to an existing group
 */
  private def addSingleUserToGroup(realmName: String, groupId: String, userId: String): Unit = {
    getRealm(realmName).users().get(userId).joinGroup(groupId)
  }


  /*
  A user can add
   - only devices → check if all passed userIds are users with role DEVICE
   - that are his own devices → need to be member of the <username>_OWN_DEVICES group of the user
   - into groups, where he is member of
   */
  private def canUserAddDeviceToGroup(realmName: String, userId: String, deviceId: String, groupId: String): Boolean = {
    val realm = getRealm(realmName)
    val userDb: UserResource = realm.users().get(userId)
    val deviceDb: UserResource = realm.users().get(deviceId)
    // check if user is a "real" user
    if (userDb.toRepresentation.getAttributes.asScala.get("type") match { case Some(value) => value.asScala.contains("device")}) throw new Exception("The user is not a user")
    // check if device is a real device
    if (!(deviceDb.toRepresentation.getAttributes.asScala.get("type") match { case Some(value) => value.asScala.contains("device")})) throw new Exception("The device is not a device")
    // check if the device belongs to the user
    val listGroupsDevice: List[Group] = getGroupsOfAUser(realmName, deviceId)
    listGroupsDevice find { g => g.name.equals(s"${userDb.toRepresentation.getUsername}_OWN_DEVICES") } match {
      case None => false
      case _ => true
    }

  }

}
