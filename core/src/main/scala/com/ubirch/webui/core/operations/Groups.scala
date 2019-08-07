package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{DeviceNotFound, GroupNotFound}
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{DeviceStubs, Group}
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{GroupRepresentation, UserRepresentation}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Try

object Groups {

  def leaveGroup(groupId: String, userId: String)(implicit realmName: String): Boolean = {
    if (isUserPartOfGroup(userId, groupId)) {
      try {
        val user = getKCUserFromId(realmName, userId)
        user.leaveGroup(groupId)
        return true
      } catch {
        case e: Exception => throw e
      }
    }
    throw new Exception(s"User with id $userId not part of group with is $groupId")
  }

  /*
  Find all the devices OR users in a group
  MUST pass a function that converts a userRepresentation either to a FrontEnd.DeviceStub or to a FrontEnd.User
   */
  def findMembersInGroup[T: ClassTag](groupId: String, memberType: String, convertToT: UserRepresentation => T)(implicit realmName: String): List[T] = {
    val group = getKCGroupFromId(realmName, groupId)
    val lMembers: List[UserRepresentation] = group.members().asScala.toList
    val lDevices = lMembers filter {m =>
      getKCUserFromId(realmName, m.getId).roles().realmLevel().listEffective().asScala.toList.contains(memberType)
    }
    lDevices map {d => convertToT(d)}
  }

  /*
  Create a group and add a user in
   */
  def createGroupAddUser(realmName: String, groupName: String, userId: String): Group = {
    val realm = getRealm(realmName)
    // see if group already exist
    Option(realm.groups().groups(groupName, 0, 1).get(0)) match {
      case Some(_) => throw new Exception(s"group $groupName already exist in realm $realmName")
    }
    val group = createGroup(realmName, groupName)
    val user = getKCUserFromId(realmName, userId)
    user.joinGroup(group.id)
    group
  }

  /*
  Create a group
   */
  def createGroup(realmName: String, name: String): Group = {
    val realm = getRealm(realmName)
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
  Delete a group if the group is empty, error otherwise
 */
  def deleteGroup(groupId: String)(implicit realmName: String): Unit = {
    val realm = getRealm
    if (!isGroupEmpty(groupId)) throw new Exception(s"Group with id $groupId is not empty")
    val groupDb = realm.groups().group(groupId)
    if (groupDb.toRepresentation.getName.contains("OWN_DEVICES")) throw new Exception(s"Group with id $groupId is a user group with name ${groupDb.toRepresentation.getName}") else {
      groupDb.remove()
    }
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
  Check if a group is empty
   */
  def isGroupEmpty(groupId: String)(implicit realmName: String) : Boolean = {
    getRealm.groups().group(groupId).members().asScala.toList match {
      case Nil => true
      case _ => false
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
  Get all users in a group
    */
  private[operations] def findAllUsersInGroup(realmName: String, groupId: String): List[UserRepresentation] = {
    val realm = getRealm(realmName)
    realm.groups().group(groupId).members().asScala.toList
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

  private def isUserPartOfGroup(userId: String, groupId: String)(implicit realmName: String): Boolean = {
    val userGroups = getKCUserFromId(realmName, groupId).groups().asScala.toList
    !userGroups.exists { g => g.getId.equals(groupId) }
  }
}
