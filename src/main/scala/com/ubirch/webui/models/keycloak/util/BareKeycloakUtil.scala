package com.ubirch.webui.models.keycloak.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ BadRequestException, InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.group.GroupFactory
import org.keycloak.admin.client.resource.{ GroupResource, UserResource }
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

    def joinGroup(groupId: String): Unit = userResource.joinGroup(groupId)

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

    def addRoles(roles: List[RoleRepresentation]) = {
      userResource.roles().realmLevel().add(roles.asJava)
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

  implicit class RichGroupResource(val groupResource: GroupResource) {

    /**
      * Contract: only one user per device group
      * Algo: take n + 1 devices
      * If user is in -> remove it
      * If user is before -> take the tail
      * If user is after -> take the first n
      * Return the desired amount of devices with the pagination desired in this user group
      * @param page Number of the page requested. Start at 0.
      * @param pageSize Number of devices returned by page.
      * @return A pageSize number of DeviceStubs. If the number of devices returned is lower, then the end of the device
      *         group has been reached
      */

    def getDevicesPagination(page: Int = 0, pageSize: Int = 100000)(implicit realmName: String): List[DeviceStub] = {
      val groupRepresentation = groupResource.toRepresentation
      if (page < 0) return Nil
      if (pageSize < 0) throw BadRequestException("page size should not be negative")
      val ownerUsername: String = groupRepresentation.getName.drop(Elements.PREFIX_OWN_DEVICES.length)
      val start = page * pageSize

      val membersInGroupPaginated: List[MemberResourceRepresentation] =
        getMembersPagination(start, pageSize + 1)
          .map(m => MemberResourceRepresentation(QuickActions.quickSearchId(m.getId), m))

      if (membersInGroupPaginated.isEmpty) {
        Nil
      } else {
        val membersSorted = membersInGroupPaginated.sortBy(m => m.representation.getUsername)
        val pureDevices = membersSorted.filterNot(m => m.representation.getUsername.toLowerCase == ownerUsername)
        val correctNumberOfDevices = if (pureDevices.size == membersSorted.size) { // if we did not find the user before
          if (pureDevices.head.representation.getUsername > ownerUsername) { // if devices are after the user
            pureDevices.tail
          } else {
            pureDevices.dropRight(1)
          }
        } else {
          pureDevices
        }
        correctNumberOfDevices.sortBy(_.representation.getUsername) map (d => DeviceStub(
          hwDeviceId = d.representation.getUsername,
          description = d.representation.getLastName,
          deviceType = d.getType
        ))
      }

    }

    def getMembersPagination(start: Int, size: Int): List[UserRepresentation] = groupResource.members(start, size).asScala.toList

    def numberOfMembers: Int = groupResource.members().size()

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

  def toSimpleUser: SimpleUser = {
    SimpleUser(
      representation.getId,
      representation.getUsername,
      representation.getLastName,
      representation.getFirstName
    )
  }

  def ifUserAuthorizedReturnDeviceFE(user: UserRepresentation)(implicit realmName: String): DeviceFE = {
    val owners = resource.getOwners
    logger.debug("owners: " + owners.map { u => u.getUsername }.mkString(", "))
    if (owners.exists(u => u.getId.equalsIgnoreCase(user.getId))) this.toDeviceFe
    else throw PermissionException(s"""Device ${toDeviceStub.toString} does not belong to user ${user.toSimpleUser.toString}""")
  }

  def getAccountInfo(implicit realmName: String): UserAccountInfo = {
    val userRoles = resource.getRoles
    val groups = resource.getAllGroups
    fullyCreate(Some(userRoles), Some(groups))
    val ownDeviceGroupRepresentation = groups.find { group =>
      group.getName.contains(Elements.PREFIX_OWN_DEVICES)
    }.get
    val ownDeviceGroupResource = GroupFactory.getByIdQuick(ownDeviceGroupRepresentation.getId)
    val numberOfDevices = ownDeviceGroupResource.members().size() - 1
    val isAdmin = userRoles.exists(_.getName == Elements.ADMIN)
    UserAccountInfo(toSimpleUser, numberOfDevices, isAdmin)
  }

  def fullyCreate(maybeRoles: Option[List[RoleRepresentation]] = None, maybeGroups: Option[List[GroupRepresentation]] = None)(implicit realmName: String): Unit = synchronized {
    val userRoles = maybeRoles.getOrElse(resource.getRoles)
    val groups = maybeGroups.getOrElse(resource.getAllGroups)
    val realm = Util.getRealm

    def doesUserHasUserRole = {
      if (userRoles.exists(r => r.getName.equalsIgnoreCase(Elements.DEVICE)))
        throw new InternalApiException("user is a device OR also has the role device")
      userRoles.exists(r => r.getName.equalsIgnoreCase(Elements.USER))
    }

    def doesUserHasOwnDeviceGroup = {
      groups.exists(group => group.getName.contains(Elements.PREFIX_OWN_DEVICES))
    }

    if (!doesUserHasUserRole) {
      resource.addRoles(List(realm.roles().get(Elements.USER).toRepresentation))
    }

    if (!doesUserHasOwnDeviceGroup) {
      val userGroupId = GroupFactory.createUserDeviceGroupQuick(representation.getUsername)
      resource.joinGroup(userGroupId)
    }
  }

  def getOwnDeviceGroup(implicit realmName: String): GroupResource = {
    val groupRepresentation = resource.getAllGroups.find { group =>
      group.getName.contains(Elements.PREFIX_OWN_DEVICES)
    }.get
    GroupFactory.getByIdQuick(groupRepresentation.getId)
  }

}

