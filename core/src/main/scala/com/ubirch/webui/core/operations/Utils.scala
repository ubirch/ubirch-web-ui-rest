package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.UserNotFound
import com.ubirch.webui.core.connector.KeyCloakConnector
import com.ubirch.webui.core.operations.Devices.removeUnwantedGroupsFromDeviceStruct
import com.ubirch.webui.core.operations.Groups.getGroupsOfAUser
import com.ubirch.webui.core.structure._
import org.keycloak.admin.client.resource.{GroupResource, RealmResource, UserResource}
import org.keycloak.representations.idm.{RoleRepresentation, UserRepresentation}

import scala.collection.JavaConverters._
import scala.util.Try

object Utils extends LazyLogging {

  /*
  Return a keycloak instance belonging to a realm
   */
  private[operations] def getRealm(implicit realmName: String): RealmResource = {
    KeyCloakConnector.get.connector.realm(realmName)
  }

  /*
  Return a full FrontEndStruct.Device representation based on its KeyCloak.UserRepresentation object
  */
  private[operations] def completeDevice(device: UserRepresentation)(implicit realmName: String): Device = {
    val deviceHwId = device.getUsername
    val creationDate = device.getCreatedTimestamp.toString
    val deviceKeyCloakId = device.getId
    val description = device.getLastName
    val groups = getGroupsOfAUser(deviceKeyCloakId)
    val deviceType = Devices.getDeviceType(deviceKeyCloakId)
    val attributes: Map[String, List[String]] = device.getAttributes.asScala.toMap map { keyValue => keyValue._1 -> keyValue._2.asScala.toList } // convert java map to scala
    Device(
      id = deviceKeyCloakId,
      hwDeviceId = deviceHwId,
      description = description,
      owner = userRepresentationToUser(Devices.getOwnerOfDevice(deviceHwId).toRepresentation),
      groups = removeUnwantedGroupsFromDeviceStruct(groups),
      attributes = attributes,
      deviceType = deviceType,
      created = creationDate
    )
  }

  private[operations] def completeDevices(devices: List[UserRepresentation])(implicit realmName: String): List[Device] = {
    val devicesOption = devices map { device => Try(completeDevice(device)) }
    devicesOption.map(_.getOrElse(null)).filter(device => device != null)
  }

  /*
  Get a KC UserResource from a username
   */
  private[operations] def getKCMemberFromUsername(userName: String)(implicit realmName: String): UserResource = {
    val realm = getRealm
    val usersOption = Option(realm.users().search(userName)) match {
      case Some(users) =>
        logger.debug(s"users with username $userName: ${users.asScala.toList.map(users => users.getUsername)}")
        users.asScala.toList.filter { user => user.getUsername.equalsIgnoreCase(userName) }
      case None => throw UserNotFound(s"user in realm $realmName with username $userName not found")
    }

    val user = usersOption match {
      case u if u.size > 1 => throw UserNotFound(s"More than one user in realm $realmName has the username $userName")
      case u if u.isEmpty => throw UserNotFound(s"No user named $userName in the realm $realmName")
      case u => u.head
    }
    realm.users().get(user.getId)
  }

  /*
  Get a KC UserResource from an id
 */
  private[operations] def getKCMemberFromId(userId: String)(implicit realmName: String): UserResource = {
    val realm = getRealm
    Option(realm.users().get(userId)) match {
      case Some(user) => user
      case None => throw UserNotFound(s"no member in realm $realmName with id $userId was found")
    }
  }

  /*
  Get a KC GroupResource from an id
  */
  private[operations] def getKCGroupFromId(groupId: String)(implicit realmName: String): GroupResource = {
    val realm = getRealm
    Option(realm.groups().group(groupId)) match {
      case Some(group) => group
      case None => throw UserNotFound(s"no user in realm $realmName with id $groupId was found")
    }
  }

  def userRepresentationToDeviceStubs(user: UserRepresentation)(implicit realmName: String): DeviceStubs = {
    DeviceStubs(user.getUsername, user.getLastName, Devices.getDeviceType(user.getId))
  }

  def userRepresentationToUser(user: UserRepresentation): User = {
    User(user.getId, user.getUsername, user.getLastName, user.getFirstName)
  }

  /*
  Check if a member is of a certain type (USER or DEVICE)
 */
  def isUserOrDevice(userId: String, userExpectedType: String)(implicit realmName: String): Boolean = {
    val t0 = System.currentTimeMillis()
    val member = getKCMemberFromId(userId)
    logger.debug(s"Took ${(System.currentTimeMillis - t0).toString} ms to get user $userId from KC")
    member.roles().realmLevel().listEffective().asScala.toList.exists { m => m.getName.equalsIgnoreCase(userExpectedType) }
  }

  def addRoleToMember(member: UserResource, role: RoleRepresentation): Unit = {
    val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
    roleRepresentationList.add(role)
    member.roles().realmLevel().add(roleRepresentationList)
  }

  def singleTypeToStupidJavaList[T](toConvert: T): util.List[T] = {
    val stupidJavaList = new util.ArrayList[T]()
    stupidJavaList.add(toConvert)
    stupidJavaList
  }

  def getIdFromUserName(userName: String)(implicit realmName: String): String = {
    getKCMemberFromUsername(userName).toRepresentation.getId
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
    val memberResource = getKCMemberFromId(kcId).toRepresentation
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
    val memberResource = getKCMemberFromUsername(userName).toRepresentation
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
  def getMemberByOneName[T, V](name: String, f: V => T, size: Int = 1)(implicit realmName: String): T = {
    val realm = getRealm
    logger.debug("name: " + name)
    val memberResource = realm.users().search(name, 0, size) match {
      case null =>
        throw UserNotFound(s"Member with name $name is not present in $realmName")
        new UserRepresentation
      case members => if (members.size() <= size) members.asScala.toList else throw UserNotFound(s"More than one member(s) with attribute $name in $realmName")
    }
    logger.debug(memberResource.asInstanceOf[V].toString)
    f(memberResource.asInstanceOf[V])
  }

  def getMemberRoles(userId: String)(implicit realmName: String): List[String] = {
    val member = getKCMemberFromId(userId)
    member.roles().realmLevel().listAll().asScala.toList map { r => r.getName }
  }

}
