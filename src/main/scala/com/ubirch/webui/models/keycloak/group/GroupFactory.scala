package com.ubirch.webui.models.keycloak.group

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.Exceptions.{ GroupNotFound, InternalApiException }
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, Util }
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.representations.idm.GroupRepresentation

import scala.util.{ Failure, Success, Try }

object GroupFactory extends LazyLogging with ConfigBase {

  def checkTenantGroupsExist(): Unit = {
    val realm = Util.getRealm(theRealmName)
    val rootTenantGroup = getOrCreateGroup(rootTenantName)(theRealmName)
    Try(getByName(s"${tenantNamePrefix}ubirch")(theRealmName)) match {
      case Failure(ex) if ex.isInstanceOf[GroupNotFound] =>
        val subGroup = new GroupRepresentation
        subGroup.setName(s"${tenantNamePrefix}ubirch")
        if (Try(realm.groups().group(rootTenantGroup.id).subGroup(subGroup)).isFailure)
          throw new InternalApiException(
            s"Subgroup with name: '${tenantNamePrefix}ubirch' cannot created."
          )
      case Success(_) => logger.info(s"Default subgroup with name: '${tenantNamePrefix}ubirch' is already exist.")
    }
  }

  def getByName(name: String)(implicit realmName: String): GroupResourceRepresentation = {
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
      getById(groupDb.getId).toResourceRepresentation // because searching by name in KC doesn't return all the attributes
    }
  }

  def getByNameQuick(name: String)(implicit realmName: String): GroupRepresentation = {
    val realm = Util.getRealm
    val groups = Try(realm.groups().groups(name, 0, 10))
    if (groups.isFailure || (groups.isSuccess && groups.get.isEmpty)) {
      throw GroupNotFound(s"Group with name $name is not present in $realmName")
    } else {
      if (groups.get.size() > 1)
        throw new InternalApiException(s"More than one group named $name in realm $realmName")
      groups.get.get(0)
    }
  }

  def getById(keyCloakId: String)(implicit realmName: String): GroupResource = {
    val group = Try(Util.getRealm.groups().group(keyCloakId))
    group.getOrElse(throw GroupNotFound(s"Group with Id $keyCloakId is not present in $realmName"))
  }

  def createUserDeviceGroupQuick(userName: String)(implicit realmName: String): GroupResourceRepresentation = {
    val nameOfGroup = Util.getDeviceGroupNameFromUserName(userName)
    getOrCreateGroup(nameOfGroup)
  }

  def createGroup(name: String)(implicit realmName: String): String = {
    val realm = Util.getRealm
    val groupStructInternal = new GroupRepresentation
    groupStructInternal.setName(name)
    val resultFromAddGroup = realm.groups().add(groupStructInternal)
    Util.getCreatedId(resultFromAddGroup)
  }

  def getOrCreateGroup(name: String)(implicit realmName: String): GroupResourceRepresentation = synchronized {
    try {
      getByNameQuick(name).toResourceRepresentation
    } catch {
      case _: GroupNotFound =>
        logger.debug(s"~~~ group with name $name not found, creating it")
        val groupId = createGroup(name)
        getById(groupId).toResourceRepresentation
    }
  }
}
