package com.ubirch.webui.core.operations

import com.ubirch.webui.core.Exceptions.UserNotFound
import com.ubirch.webui.core.connector.KeyCloakConnector
import com.ubirch.webui.core.operations.Groups.getGroupsOfAUser
import com.ubirch.webui.core.structure._
import org.keycloak.admin.client.resource.{GroupResource, RealmResource, UserResource}
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters._

object Utils {


  /*
  Return a keycloak instance belonging to a realm
   */
  private[operations] def getRealm(implicit realmName: String): RealmResource = {
    KeyCloakConnector.get.kc.realm(realmName)
  }


  /*
  Return a full FrontEndStruct.Device representation based on its KeyCloack.UserRepresentation object
  */
  private[operations] def completeDevice(device: UserRepresentation, realmName: String): Device = {
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
  Get a user representation from its userName and the realm he belongs to.
  Assumes that (username, realm) is a primary key (unique).
   */
  private[operations] def getUserRepresentationFromUserName(realmName: String, userName: String): UserRepresentation = {
    val realm = getRealm(realmName)
    val userInternalOption = realm.users().search(userName).asScala.headOption match {
      case Some(value) => value
      case None => throw UserNotFound(s"user with username $userName is not present in $realmName")
    }
    userInternalOption
  }


  /*
  Get a KC UserResource from a username
   */
  private[operations] def getKCUserFromUsername(realmName: String, userName: String): UserResource = {
    val realm = getRealm(realmName)
    val userOption = Option(realm.users().search(userName)) match {
      case Some(v) => v
      case None => throw new Exception(s"user in realm $realmName with username $userName not found")
    }

    val user = userOption match {
      case x if x.size() > 1 => throw new Exception(s"More than one user in realm $realmName has the username $userName")
      case y => y.get(0)
    }
    realm.users().get(user.getId)
  }

  /*
Get a KC UserResource from an id
 */
  private[operations] def getKCUserFromId(realmName: String, id: String): UserResource = {
    val realm = getRealm(realmName)
    Option(realm.users().get(id)) match {
      case Some(value) => value
      case None => throw new Exception(s"no user in realm $realmName with id $id was found")
    }
  }

  /*
  Get a KC GroupResource from an id
  */
  private[operations] def getKCGroupFromId(realmName: String, id: String): GroupResource = {
    val realm = getRealm(realmName)
    Option(realm.groups().group(id)) match {
      case Some(value) => value
      case None => throw new Exception(s"no user in realm $realmName with id $id was found")
    }
  }

  def userRepresentationToDeviceStubs(userRepresentation: UserRepresentation): DeviceStubs = {
    DeviceStubs(userRepresentation.getUsername, userRepresentation.getLastName)
  }

  def userRepresentationToUser(userRepresentation: UserRepresentation): User = {
    User(userRepresentation.getId, userRepresentation.getUsername, userRepresentation.getLastName, userRepresentation.getFirstName)
  }

}
