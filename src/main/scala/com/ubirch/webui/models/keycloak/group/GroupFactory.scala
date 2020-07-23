package com.ubirch.webui.models.keycloak.group

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Exceptions.{ GroupNotFound, InternalApiException }
import com.ubirch.webui.models.keycloak.util.Util
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.representations.idm.GroupRepresentation

import scala.util.Try

object GroupFactory extends LazyLogging {

  def getByName(name: String)(implicit realmName: String): Group = {
    val realm = Util.getRealm
    val groups = Try(realm.groups().groups(name, 0, 10))
    if (groups.isFailure || (groups.isSuccess && groups.get.isEmpty)) {
      throw GroupNotFound(s"Group with name $name is not present in $realmName")
    } else {
      if (groups.get.size() > 1)
        throw new InternalApiException(
          s"More than one group named $name in realm $realmName"
        )
      val groupDb = groups.get.get(0)
      getById(groupDb.getId) // because searching by name in KC doesn't return all the attributes
    }
  }

  def getById(keyCloakId: String)(implicit realmName: String): Group = {
    val group = Try(Util.getRealm.groups().group(keyCloakId))
    if (group.isFailure) throw GroupNotFound(s"Group with Id $keyCloakId is not present in $realmName")
    else new Group(group.get)
  }

  def getByIdQuick(keyCloakId: String)(implicit realmName: String): GroupResource = {
    val group = Try(Util.getRealm.groups().group(keyCloakId))
    group.getOrElse(throw GroupNotFound(s"Group with Id $keyCloakId is not present in $realmName"))
  }

  def createUserDeviceGroup(userName: String)(implicit realmName: String): Group = {
    val nameOfGroup = Util.getDeviceGroupNameFromUserName(userName)
    createGroup(nameOfGroup)
  }

  def createUserDeviceGroupQuick(userName: String)(implicit realmName: String): String = {
    val nameOfGroup = Util.getDeviceGroupNameFromUserName(userName)
    createGroupQuick(nameOfGroup)
  }

  def createGroup(name: String)(implicit realmName: String): Group = {
    val realm = Util.getRealm
    val groupStructInternal = new GroupRepresentation
    groupStructInternal.setName(name)
    val resultFromAddGroup = realm.groups().add(groupStructInternal)
    val groupId = Util.getCreatedId(resultFromAddGroup)
    getById(groupId)
  }

  def createGroupQuick(name: String)(implicit realmName: String): String = {
    val realm = Util.getRealm
    val groupStructInternal = new GroupRepresentation
    groupStructInternal.setName(name)
    val resultFromAddGroup = realm.groups().add(groupStructInternal)
    Util.getCreatedId(resultFromAddGroup)
  }

  def getOrCreateGroup(name: String)(implicit realmName: String): Group = synchronized {
    try {
      getByName(name)
    } catch {
      case _: GroupNotFound =>
        logger.debug(s"~~~ group with name $name not found, creating it")
        createGroup(name)
    }
  }
}
