package com.ubirch.webui.core.operations

import java.util

import com.ubirch.webui.core.Exceptions.UserNotFound
import com.ubirch.webui.core.connector.KeyCloakConnector
import com.ubirch.webui.core.operations.Devices.removeUnwantedGroupsFromDeviceStruct
import com.ubirch.webui.core.operations.Groups.getGroupsOfAUser
import com.ubirch.webui.core.structure._
import org.keycloak.admin.client.resource.{GroupResource, RealmResource, UserResource}
import org.keycloak.representations.idm.{RoleRepresentation, UserRepresentation}

import scala.collection.JavaConverters._

object Utils {

  /*
  Return a keycloak instance belonging to a realm
   */
  private[operations] def getRealm(implicit realmName: String): RealmResource = {
    KeyCloakConnector.get.kc.realm(realmName)
  }

  /*
  Return a full FrontEndStruct.Device representation based on its KeyCloak.UserRepresentation object
  */
  private[operations] def completeDevice(device: UserRepresentation)(implicit realmName: String): Device = {
    val deviceHwId = device.getUsername
    val deviceInternalId = device.getId
    val description = device.getLastName
    val lGroups = getGroupsOfAUser(deviceInternalId)
    val deviceType = Devices.getDeviceType(deviceInternalId)
    val attributes: Map[String, List[String]] = device.getAttributes.asScala.toMap map { x => x._1 -> x._2.asScala.toList }
    val deviceWithUnwantedGroups = Device(
      deviceInternalId,
      deviceHwId,
      description,
      owner = userRepresentationToUser(Devices.getOwnerOfDevice(deviceHwId).toRepresentation),
      groups = lGroups,
      attributes,
      deviceType
    )
    removeUnwantedGroupsFromDeviceStruct(deviceWithUnwantedGroups)
  }

  /*
  Get a KC UserResource from a username
   */
  private[operations] def getKCUserFromUsername(userName: String)(implicit realmName: String): UserResource = {
    val realm = getRealm(realmName)
    val userOption = Option(realm.users().search(userName)) match {
      case Some(v) => v
      case None => throw new Exception(s"user in realm $realmName with username $userName not found")
    }

    val user = userOption match {
      case x if x.size() > 1 => throw new Exception(s"More than one user in realm $realmName has the username $userName")
      case x if x.size() == 0 => throw UserNotFound(s"No user named $userName in the realm $realmName")
      case y => y.get(0)
    }
    realm.users().get(user.getId)
  }

  /*
Get a KC UserResource from an id
 */
  private[operations] def getKCUserFromId(id: String)(implicit realmName: String): UserResource = {
    val realm = getRealm
    Option(realm.users().get(id)) match {
      case Some(value) => value
      case None => throw new Exception(s"no user in realm $realmName with id $id was found")
    }
  }

  /*
  Get a KC GroupResource from an id
  */
  private[operations] def getKCGroupFromId(id: String)(implicit realmName: String): GroupResource = {
    val realm = getRealm
    Option(realm.groups().group(id)) match {
      case Some(value) => value
      case None => throw new Exception(s"no user in realm $realmName with id $id was found")
    }
  }

  def userRepresentationToDeviceStubs(userRepresentation: UserRepresentation)(implicit realmName: String): DeviceStubs = {
    DeviceStubs(userRepresentation.getUsername, userRepresentation.getLastName, Devices.getDeviceType(userRepresentation.getId))
  }

  def userRepresentationToUser(userRepresentation: UserRepresentation): User = {
    User(userRepresentation.getId, userRepresentation.getUsername, userRepresentation.getLastName, userRepresentation.getFirstName)
  }

  /*
  Check if a member is of a certain type (USER or DEVICE)
 */
  def isUserOrDevice(userId: String, userExpectedType: String)(implicit realmName: String): Boolean = {
    val uRes = getKCUserFromId(userId)
    uRes.roles().realmLevel().listEffective().asScala.toList.exists { v => v.getName.equals(userExpectedType) }
  }

  def addRoleToUser(user: UserResource, role: RoleRepresentation): Unit = {
    val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
    roleRepresentationList.add(role)
    user.roles().realmLevel().add(roleRepresentationList)
  }

  def singleTypeToStupidJavaList[T](toConvert: T): util.List[T] = {
    val stupidJavaList = new util.ArrayList[T]()
    stupidJavaList.add(toConvert)
    stupidJavaList
  }

  def getIdFromUserName(userName: String)(implicit realmName: String): String = {
    getKCUserFromUsername(userName).toRepresentation.getId
  }

  /**
    * Get a member of KeyCloak (normal user or device user) from its KeyCloak ID.
    *
    * @param kcId      KeyCloak ID of the user.
    * @param f         Function converting a UserRepresentation into a T value.
    * @param realmName Name of the realm we're operating on.
    * @tparam T Type of member: Device or User.
    * @return Member representation.
    */
  def getMemberById[T](kcId: String, f: UserRepresentation => T)(implicit realmName: String): T = {
    val memberResource = getKCUserFromId(kcId).toRepresentation
    f(memberResource)
  }

  /**
    * Get a member of KeyCloak (normal user or device user) from its userName.
    *
    * @param userName  UserName of the user.
    * @param f         Function converting a UserRepresentation into a T value.
    * @param realmName Name of the realm we're operating on.
    * @tparam T Type of member: Device or User.
    * @return Member representation.
    */
  def getMemberByUsername[T](userName: String, f: UserRepresentation => T)(implicit realmName: String): T = {
    val memberResource = getKCUserFromUsername(userName).toRepresentation
    f(memberResource)
  }

  /**
    * Get a member of KeyCloak (normal user or device user) from its last or first name.
    *
    * @param name      First or last name of the user.
    * @param f         Function converting a UserRepresentation into a T value.
    * @param realmName Name of the realm we're operating on.
    * @tparam T Type of member: Device or User.
    * @return Member representation.
    */
  def getMemberByOneName[T](name: String, f: UserRepresentation => T)(implicit realmName: String): T = {
    val realm = getRealm
    val memberResource: UserRepresentation = realm.users().search(name, 0, 1) match {
      case null =>
        throw new Exception(s"Member with name $name is not present in $realmName")
        new UserRepresentation
      case x => if (x.size() == 1) x.get(0) else throw new Exception(s"More than one member with name $name in $realmName")
    }
    f(memberResource)
  }

}