package com.ubirch.webui.models.keycloak.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak.{ DeviceDumb, DeviceFE, DeviceStub, GroupFE, SimpleUser }
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

import scala.collection.JavaConverters._
import scala.util.Try

package object BareKeycloakUtil {

  implicit class RichUserResource(val userResource: UserResource) {

    def isDevice: Boolean = {
      userResource.getRoles.exists {
        m => m.getName.equalsIgnoreCase(Elements.DEVICE)
      }
    }

    def isUser: Boolean = {
      userResource.getRoles.exists {
        m => m.getName.equalsIgnoreCase(Elements.USER)
      }
    }

    def getAllGroups: List[GroupRepresentation] = userResource.groups().asScala.toList

    def getRoles: List[RoleRepresentation] = userResource.roles().realmLevel().listEffective().asScala.toList

    def deviceType: String = {
      userResource.getAllGroups.find { group => group.getName.contains(Elements.PREFIX_DEVICE_TYPE) } match {
        case Some(group) => group.getName.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
        case None => throw new InternalApiException(s"Device with Id ${userResource.toRepresentation.getId} has no type")
      }
    }

    def getOwners(implicit realmName: String): List[UserRepresentation] = {
      val ownerGroups = getAllGroups
        .filter { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) }
        .map { group => group.getName.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }
      if (ownerGroups.isEmpty) {
        Nil
      } else {
        ownerGroups map { username => QuickActions.quickSearchUserNameOnlyOne(username) }
      }
    }

  }

  implicit class RichUserRepresentation(val userRepresentation: UserRepresentation) {
    def toSimpleUser: SimpleUser = {
      SimpleUser(
        userRepresentation.getId,
        userRepresentation.getUsername,
        userRepresentation.getLastName,
        userRepresentation.getFirstName
      )
    }
  }

}

case class MemberResourceRepresentation(resource: UserResource, representation: UserRepresentation) extends LazyLogging {

  import BareKeycloakUtil._

  def getType: String = resource.deviceType

  def toDeviceFe(implicit realmName: String): DeviceFE = {
    val t0 = System.currentTimeMillis()
    val allGroupsRepresentation = resource.getAllGroups

    val groupsWithoutUnwantedOnes = allGroupsRepresentation
      .filter { group => !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API) || group.getName.contains(Elements.PREFIX_OWN_DEVICES)) }
      .map { representation => GroupFE(representation.getId, representation.getName) }

    val owners = Try {
      val ownerGroups = allGroupsRepresentation
        .filter { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) }
        .map { group => group.getName.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }
      if (ownerGroups.isEmpty) {
        Nil
      } else {
        ownerGroups map { username =>
          val userRepresentation = QuickActions.quickSearchUserNameOnlyOne(username)
          SimpleUser(
            userRepresentation.getId,
            userRepresentation.getUsername,
            userRepresentation.getLastName,
            userRepresentation.getFirstName
          )
        }
      }
    }

    val res = DeviceFE(
      id = representation.getId,
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      owner = owners.getOrElse(Nil),
      groups = groupsWithoutUnwantedOnes,
      attributes = Converter.attributesToMap(representation.getAttributes),
      deviceType = resource.deviceType,
      created = representation.getCreatedTimestamp.toString
    )

    logger.debug(s"~~ Time to toDeviceFE = ${System.currentTimeMillis() - t0}ms")
    res
  }

  def toDeviceDumb(implicit realmName: String): DeviceDumb = {
    DeviceDumb(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      customerId = Util.getCustomerId(realmName)
    )
  }

  def toDeviceStub: DeviceStub = {
    DeviceStub(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      deviceType = resource.deviceType
    )
  }

  def ifUserAuthorizedReturnDeviceFE(user: UserRepresentation)(implicit realmName: String): DeviceFE = {
    val owners = resource.getOwners
    logger.debug("owners: " + owners.map { u => u.getUsername }.mkString(", "))
    if (owners.exists(u => u.getId.equalsIgnoreCase(user.getId))) this.toDeviceFe
    else throw PermissionException(s"""Device ${toDeviceStub.toString} does not belong to user ${user.toSimpleUser.toString}""")
  }

}

