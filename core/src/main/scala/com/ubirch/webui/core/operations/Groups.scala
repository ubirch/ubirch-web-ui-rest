package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{DeviceNotFound, GroupNotEmpty, GroupNotFound}
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.Group
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{GroupRepresentation, UserRepresentation}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Try

object Groups {

  def leaveGroup(userId: String, groupId: String)(implicit realmName: String): Boolean = {
    if (isUserPartOfGroup(userId, groupId)) {
      try {
        val user = getKCUserFromId(userId)
        user.leaveGroup(groupId)
        true
      } catch {
        case e: Exception => throw e
      }
    } else {
      throw new Exception(s"User with id $userId is not part of the group with id $groupId")
      false
    }
  }

  /*
  Find all the devices OR users in a group
  MUST pass a function that converts a userRepresentation either to a FrontEnd.DeviceStub or to a FrontEnd.User
   */
  def findMembersInGroup[T: ClassTag](groupId: String, memberType: String, convertToT: UserRepresentation => T)(implicit realmName: String): List[T] = {
    val group = getKCGroupFromId(groupId)
    val lMembers: List[UserRepresentation] = group.members().asScala.toList
    val lDevices = lMembers filter { m => isOfType(m.getId, memberType) }
    lDevices map {d => convertToT(d)}
  }

  /*
  Create a group and add a user in
   */
  def createGroupAddUser(groupName: String, userId: String)(implicit realmName: String): Group = {
    val realm = getRealm
    // see if group already exist
    if (!realm.groups().groups(groupName, 0, 1).isEmpty) {
      throw new Exception(s"group $groupName already exist in realm $realmName")
    }
    val group = createGroup(groupName)
    val user = getKCUserFromId(userId)
    user.joinGroup(group.id)
    group
  }

  /*
  Create a group
   */
  def createGroup(name: String)(implicit realmName: String): Group = {
    val realm = getRealm
    val groupStructInternal = new GroupRepresentation
    groupStructInternal.setName(name)
    val res = realm.groups().add(groupStructInternal)
    val idGroup = ApiUtil.getCreatedId(res)
    Group(idGroup, name)
  }

  /*
  Find a group based on its name
   */
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

  /*
  Find a group based on its ID
   */
  def findGroupById(groupId: String)(implicit realmName: String): Group = {
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
  Delete a group if the group is empty, error otherwise
 */
  def deleteGroup(groupId: String)(implicit realmName: String): Unit = {
    val realm = getRealm

    if (!doesGroupExist(groupId)) throw GroupNotFound(s"Group with id $groupId doesn't exist")

    if (!isGroupEmpty(groupId)) throw GroupNotEmpty(s"Group with id $groupId is not empty")
    val groupDb = realm.groups().group(groupId)
    if (groupDb.toRepresentation.getName.contains("OWN_DEVICES")) throw new Exception(s"Group with id $groupId is a user group with name ${groupDb.toRepresentation.getName}") else {
      groupDb.remove()
    }
  }

  /*
  Create a device group for a user named "<username>_OWN_DEVICES"
  Every user has one
   */
  def createUserDeviceGroup(userInternal: UserRepresentation)(implicit realmName: String): Group = {
    val nameOfGroup = s"${userInternal.getUsername}_OWN_DEVICES"
    val group = createGroup(nameOfGroup)
    addSingleUserToGroup(group.id, userInternal.getId)
    group
  }

  /*
  Get all the groups a user is subscribed to EXCEPT SPECIAL USER GROUP OWN_DEVICE
 */
  def getGroupsOfAUser(userId: String)(implicit realmName: String): List[Group] = {
    val realm = getRealm(realmName)
    val groupsDb: mutable.Seq[GroupRepresentation] = Option(realm.users().get(userId).groups().asScala) match {
      case Some(value) => value
      case None =>
        throw DeviceNotFound(s"user/device with id $userId is not present in $realmName")
    }
    val groupsUnsorted = groupsDb.map { g => Group(g.getId, g.getName) }.toList
    groupsUnsorted.filter(g => !g.name.contains("_OWN_DEVICES"))
  }

  /*
  Get all the groups of a user, includind the own_users group
   */
  private[operations] def getAllGroupsOfAUser(userId: String)(implicit realmName: String): List[Group] = {
    val realm = getRealm(realmName)
    val groupsDb: mutable.Seq[GroupRepresentation] = Option(realm.users().get(userId).groups().asScala) match {
      case Some(value) => value
      case None =>
        throw DeviceNotFound(s"user/device with id $userId is not present in $realmName")
    }
    groupsDb.map { g => Group(g.getId, g.getName) }.toList
  }

  /*
  Check if a group is empty
   */
  def isGroupEmpty(groupId: String)(implicit realmName: String) : Boolean = {
    if (!doesGroupExist(groupId)) throw GroupNotFound(s"Group with id $groupId doesn't exist")
    getRealm.groups().group(groupId).members().asScala.toList match {
      case Nil => true
      case _ => false
    }
  }

  /*
  Check if a group exist
   */
  def doesGroupExist(groupId: String)(implicit realmName: String): Boolean = {
    try {
      getRealm.groups().group(groupId).toRepresentation
      true
    } catch {
      case _: NotFoundException => false
    }
  }

  /*
  Add devices from users to a group
   */
  def addDevicesFromUserToGroup(userId: String, devicesId: List[String], groupId: String)(implicit realmName: String): Unit = {
    devicesId foreach { d => addDeviceFromUserToGroup(userId, d, groupId) }
  }

  /*
  Add a device to a group from a user
 */
  private def addDeviceFromUserToGroup(userId: String, deviceId: String, groupId: String)(implicit realmName: String): Boolean = {
    if (!canUserAddDeviceToGroup(userId, deviceId, groupId))
      throw new Exception("User cannot add device to group") else {
      try {
        val device = getRealm.users().get(deviceId)
        println("group: " + groupId)
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
  Get all users in a group
    */
  private[operations] def findAllUsersInGroup(realmName: String, groupId: String): List[UserRepresentation] = {
    val realm = getRealm(realmName)
    realm.groups().group(groupId).members().asScala.toList
  }



  /*
  Add a user (device or normal user) to an existing group
 */
  private[operations] def addSingleUserToGroup(groupId: String, userId: String)(implicit realmName: String): Unit = {
    getRealm.users().get(userId).joinGroup(groupId)
  }

  /*
  A user can add
   - only devices → check if all passed userIds are users with role DEVICE
   - that are his own devices → need to be member of the <username>_OWN_DEVICES group of the user
   - into groups, where he is member of
 */
  private def canUserAddDeviceToGroup(userId: String, deviceId: String, groupId: String)(implicit realmName: String): Boolean = {
    val realm = getRealm
    val userDb: UserResource = realm.users().get(userId)
    val deviceDb: UserResource = realm.users().get(deviceId)
    // check if user is a "real" user
    if (!userDb.roles().realmLevel().listEffective().asScala.exists(r => r.getName.equals("USER"))) throw new Exception("The user is not a user")
    // check if device is a real device
    if (!deviceDb.roles().realmLevel().listEffective().asScala.exists(r => r.getName.equals("DEVICE"))) throw new Exception("The device is not a device")
    // check if the device belongs to the user
    val listGroupsDevice: List[Group] = getAllGroupsOfAUser(deviceId)
    listGroupsDevice find { g => g.name.equals(s"${userDb.toRepresentation.getUsername}_OWN_DEVICES") } match {
      case None => false
      case _ => true
    }

  }

  private def isUserPartOfGroup(userId: String, groupId: String)(implicit realmName: String): Boolean = {
    val userGroups = getKCUserFromId(userId).groups().asScala.toList
    userGroups.exists { g => g.getId.equals(groupId) }
  }
}
