package com.ubirch.webui.models.keycloak.util

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.api.{ Claims, InvalidClaimException }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ BadOwner, BadRequestException, DeviceAlreadyClaimedException, GroupNotEmpty, GroupNotFound, InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member._

import javax.ws.rs.WebApplicationException
import org.keycloak.admin.client.resource.{ GroupResource, UserResource }
import org.keycloak.representations.idm.{ CredentialRepresentation, GroupRepresentation, RoleRepresentation, UserRepresentation }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

package object BareKeycloakUtil {

  implicit class RichUserResource(val userResource: UserResource) {

    /**
      * Will throw an exception if the device has already been claimed
      */
    def stopIfDeviceAlreadyClaimed(): Unit = if (userResource.isClaimed()) throw DeviceAlreadyClaimedException("Device already claimed.")

    /**
      * @return True is the keycloak resource is assigned the role Device
      */
    def isDevice: Boolean = getRoles.exists(_.getName.equalsIgnoreCase(Elements.DEVICE))

    /**
      * @return True is the keycloak resource is assigned the role User
      */
    def isUser: Boolean = getRoles.exists(_.getName.equalsIgnoreCase(Elements.USER))

    /**
      * @return True is the keycloak resource is assigned the role Admin
      */
    def isAdmin: Boolean = getRoles.exists(_.getName.equalsIgnoreCase(Elements.ADMIN))

    /**
      * When used as a device, will check if the user passed as an argument is an owner of the device
      * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
      *                       (can be used to reduce the amount of queries against the backend).
      */
    def isUserAuthorized(user: UserRepresentation, maybeAllGroups: Option[List[GroupRepresentation]] = None)(implicit realmName: String): Boolean = {
      getOwners(maybeAllGroups).exists(u => u.getId.equalsIgnoreCase(user.getId))
    }

    /**
      * Return all the groups of the user. The maybeAllGroups serves as a cache.
      * @param maybeAllGroups will return this if not None.
      * @return All the groups of the user.
      */
    def getAllGroups(maybeAllGroups: Option[List[GroupRepresentation]] = None): List[GroupRepresentation] =
      maybeAllGroups.getOrElse(userResource.groups().asScala.toList)

    /**
      * @return All the roles associated to the user.
      */
    def getRoles: List[RoleRepresentation] = userResource.roles().realmLevel().listEffective().asScala.toList

    /**
      * Return the type of the device
      * Contract: the keycloak resource is a device
      * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
      *                       (can be used to reduce the amount of queries against the backend).
      * @return The name of the type associated to the device.
      */
    def getDeviceType(maybeAllGroups: Option[List[GroupRepresentation]] = None): String = {
      userResource.getDeviceGroup(maybeAllGroups) match {
        case Some(group) => group.getName.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
        case None => throw new InternalApiException(s"Device with Id ${userResource.toRepresentation.getId} has no type")
      }
    }

    /**
      * Return the deviceGroup of the device. This device group contains the name and attribute of the device type
      * associated to the device
      * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
      *                       (can be used to reduce the amount of queries against the backend).
      */
    def getDeviceGroup(maybeAllGroups: Option[List[GroupRepresentation]] = None): Option[GroupRepresentation] = userResource.getAllGroups(maybeAllGroups).find { group => group.getName.contains(Elements.PREFIX_DEVICE_TYPE) }

    /**
      * Return the list of all the owners of the device
      * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
      *                       (can be used to reduce the amount of queries against the backend).
      * @param realmName The name of the realm.
      * @return Return a list of UserRepresentation of the owners of the device.
      */
    def getOwners(maybeAllGroups: Option[List[GroupRepresentation]] = None)(implicit realmName: String): List[UserRepresentation] = {

      val ownerGroups = userResource
        .getAllGroups(maybeAllGroups)
        .filter { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) }
        .map { group => group.getName.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }
      if (ownerGroups.isEmpty) {
        Nil
      } else {
        ownerGroups map { username => QuickActions.quickSearchUserNameOnlyOne(username) }
      }
    }

    /**
      * Add the list of roles to the user.
      * The roles must already exist on keycloak, otherwise it'll throw a javax.ws.rs.NotFoundException
      */
    def addRoles(roles: List[RoleRepresentation]): Unit = {
      userResource.roles().realmLevel().add(roles.asJava)
    }

    /**
      * Will case the resource to a MemberResourceRepresentation object by fetching the representation of the resource
      */
    def toResourceRepresentation(implicit realmName: String): MemberResourceRepresentation = {
      MemberResourceRepresentation(userResource, userResource.toRepresentation)
    }

    /**
      * Check if the device is an IMSI. If yes, return false
      */
    def canBeDeleted(maybeAllGroups: Option[List[GroupRepresentation]] = None): Boolean = {
      !userResource.getAllGroups(maybeAllGroups).exists(g => g.getName.toLowerCase.contains(Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX.toLowerCase))
    }

    /**
      * Check if a device belongs to a user_OWN_DEVICES group
      */
    def isClaimed(maybeAllGroups: Option[List[GroupRepresentation]] = None): Boolean =
      userResource.getAllGroups(maybeAllGroups).exists(g => g.getName.toLowerCase.contains(Elements.PREFIX_OWN_DEVICES.toLowerCase))

    def getProviderName(maybeAllGroups: Option[List[GroupRepresentation]] = None): String = {
      userResource.getAllGroups(maybeAllGroups)
        .find(p => p.getName.contains(Elements.PROVIDER_GROUP_PREFIX))
        .map(_.getName)
        .getOrElse("")
        .replace(Elements.PROVIDER_GROUP_PREFIX, "")
    }

    /**
      * This method determines the provider of the device and return the claimed group CLAIMED_provider_name of the device.
      * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
      *                       (can be used to reduce the amount of queries against the backend).
      */
    def getClaimedProviderGroup(maybeAllGroups: Option[List[GroupRepresentation]] = None): Option[GroupRepresentation] = {

      val allGroups = getAllGroups(maybeAllGroups)

      val providerName = getProviderName(Some(allGroups))
      allGroups.find(g => g.getName.equalsIgnoreCase(Util.getProviderClaimedDevicesName(providerName)))
    }

    /**
      * Expect that the user only belongs to one OWN_DEVICES group
      */
    def getOwnDeviceGroup(maybeAllGroups: Option[List[GroupRepresentation]] = None): GroupRepresentation = {
      userResource.getAllGroups(maybeAllGroups).find { group =>
        group.getName.contains(Elements.PREFIX_OWN_DEVICES)
      }.get
    }

    def getUpdatedResource(implicit realmName: String): UserResource = QuickActions.quickSearchId(userResource.toRepresentation.getId)

  }

  implicit class RichUserRepresentation(val userRepresentation: UserRepresentation) {

    def deleteOwnDevice(device: MemberResourceRepresentation)(implicit realmName: String): Unit = {
      if (device.resource.getOwners().exists(u => u.getId.equalsIgnoreCase(userRepresentation.getId))) {
        device.representation.delete
      } else throw BadOwner("device does not belong to user")
    }

    def getUpdatedResource(implicit realmName: String): UserResource = QuickActions.quickSearchId(userRepresentation.getId)

    def toSimpleUser: SimpleUser = {
      SimpleUser(
        userRepresentation.getId,
        userRepresentation.getUsername,
        userRepresentation.getLastName,
        userRepresentation.getFirstName
      )
    }

    def toResourceRepresentation(implicit realmName: String): MemberResourceRepresentation = {
      MemberResourceRepresentation(QuickActions.quickSearchId(userRepresentation.getId), userRepresentation)
    }

    protected[keycloak] def delete(implicit realmName: String): Unit =
      Util.getRealm.users().get(userRepresentation.getId).remove()

  }

  implicit class RichGroupRepresentation(val groupRepresentation: GroupRepresentation) {

    def toResourceRepresentation(implicit realmName: String): GroupResourceRepresentation = {
      if (Option(groupRepresentation.getAttributes).isEmpty) {
        val resource = GroupFactory.getById(groupRepresentation.getId)
        GroupResourceRepresentation(resource, resource.toRepresentation)
      } else {
        GroupResourceRepresentation(GroupFactory.getById(groupRepresentation.getId), groupRepresentation)
      }
    }

    def toGroupFE: GroupFE = {
      GroupFE(groupRepresentation.getId, groupRepresentation.getName)
    }

    def getOrganizationalUnitIdOf(name: String): Option[String] =
      groupRepresentation.getSubGroups.asScala.toList.flatMap(_.getSubGroups.asScala.toList).find(_.getName == name).map(_.getId)

  }

  implicit class RichGroupResource(val groupResource: GroupResource) {

    def toResourceRepresentation(implicit realmName: String) = GroupResourceRepresentation(groupResource, groupResource.toRepresentation)

    def isEmpty: Boolean = numberOfMembers == 0

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
        correctNumberOfDevices.sortBy(_.representation.getUsername) map (d => d.toDeviceStub())
      }

    }

    def getMembersPagination(start: Int, size: Int): List[UserRepresentation] = groupResource.members(start, size).asScala.toList

    def numberOfMembers: Int = groupResource.members().size()

    /**
      * Get all the members of a group. Returns a maximum of 100 individuals
      * @return A maximum of 100 users in a group
      */
    def getMembers: List[UserRepresentation] = groupResource.members().asScala.toList

    def getMaxCount(maxCount: Int = Int.MaxValue): Int = groupResource.members(0, maxCount).size()

  }

}

case class MemberResourceRepresentation(resource: UserResource, representation: UserRepresentation)(implicit realmName: String) extends LazyLogging {

  import BareKeycloakUtil._

  def getKeycloakId: String = representation.getId

  def getUsername: String = representation.getUsername

  def getLastName: String = representation.getLastName

  def getHwDeviceId: String = representation.getUsername

  def getSecondaryIndex: String = representation.getFirstName

  def getDescription: String = representation.getLastName

  def getAttributesScala: Map[String, List[String]] = {
    if (Option(representation.getAttributes).isEmpty) {
      Util.attributesToMap(resource.toRepresentation.getAttributes)
    } else {
      Util.attributesToMap(representation.getAttributes)
    }
  }

  def getGroupsFiltered: List[GroupRepresentation] =
    resource.getAllGroups().filter(group => !group.getName.contains(Elements.PREFIX_OWN_DEVICES))

  def getType(maybeGroups: Option[List[GroupRepresentation]] = None): String = resource.getDeviceType(maybeGroups)

  def isUser: Boolean = resource.isUser
  def isDevice: Boolean = resource.isDevice

  def addDevicesToGroup(devices: List[MemberResourceRepresentation], group: GroupRepresentation): Unit = {
    devices foreach (d => addDeviceToGroup(d, group))
  }

  def addDeviceToGroup(device: MemberResourceRepresentation, group: GroupRepresentation): Unit = {
    if (canUserAddDeviceToGroup(device)) {
      device.joinGroupById(group.getId)
    } else throw PermissionException("User cannot add device to group")
  }

  def canUserAddDeviceToGroup(device: MemberResourceRepresentation): Boolean = {
    if (!device.resource.isDevice)
      throw new InternalApiException("The device is not a device")
    if (resource.isDevice)
      throw new InternalApiException("The user is not a user in KC")
    device.resource.getOwners().exists(u => u.getId.equalsIgnoreCase(representation.getId))
  }

  def getGroups(maybeAllGroups: Option[List[GroupRepresentation]] = None): List[GroupFE] = {
    resource
      .getAllGroups(maybeAllGroups)
      .filter { group => !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API)) }
      .map { representation => GroupFE(representation.getId, representation.getName) }
  }

  def getOwners: Try[List[SimpleUser]] = {
    Try(resource.getOwners(None).map(_.toSimpleUser))
  }

  def toDeviceFE(maybeAllGroups: Option[List[GroupRepresentation]] = None): DeviceFE = {
    val t0 = System.currentTimeMillis()
    val allGroupsRepresentation = resource.getAllGroups(maybeAllGroups)

    val groupsWithoutUnwantedOnes = allGroupsRepresentation
      .filter { group => !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API) || group.getName.contains(Elements.PREFIX_OWN_DEVICES)) }
      .map { representation => GroupFE(representation.getId, representation.getName) }

    val owners = Try(resource.getOwners(maybeAllGroups).map(_.toSimpleUser))

    val res = DeviceFE(
      id = representation.getId,
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      owner = owners.getOrElse(Nil),
      groups = groupsWithoutUnwantedOnes,
      attributes = getAttributesScala,
      deviceType = resource.getDeviceType(Some(allGroupsRepresentation)),
      created = representation.getCreatedTimestamp.toString,
      canBeDeleted = resource.canBeDeleted(Some(allGroupsRepresentation))
    )

    logger.info(s"~~ Time to toDeviceFE = ${System.currentTimeMillis() - t0}ms")
    res
  }

  /**
    *
    * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
    *                       (can be used to reduce the amount of queries against the backend).
    */
  def toAddDevice(maybeAllGroups: Option[List[GroupRepresentation]] = None): AddDevice = {
    val groups = resource.getAllGroups(maybeAllGroups)
    val deviceFE = toDeviceFE(Some(groups))
    AddDevice(
      hwDeviceId = deviceFE.hwDeviceId,
      description = deviceFE.description,
      deviceType = deviceFE.deviceType,
      listGroups = groups.map { g => g.getName },
      attributes = deviceFE.attributes,
      secondaryIndex = this.getSecondaryIndex
    )
  }

  def toDeviceDumb: DeviceDumb = {
    val owners = getOwners.getOrElse(throw new Exception("Error retrieving owners"))
    DeviceDumb(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      customerId = owners.headOption.map(_.id).getOrElse(representation.getId),
      owners = owners
    )
  }

  def toDeviceStub(maybeAllGroups: Option[List[GroupRepresentation]] = None): DeviceStub = {
    DeviceStub(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      deviceType = resource.getDeviceType(maybeAllGroups),
      canBeDeleted = resource.canBeDeleted(maybeAllGroups)
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

  def ifUserAuthorizedReturnDeviceFE(device: UserRepresentation): DeviceFE = {
    val owners = resource.getOwners()
    logger.debug("owners: " + owners.map { u => u.getUsername }.mkString(", "))
    if (owners.exists(u => u.getId.equalsIgnoreCase(device.getId))) this.toDeviceFE()
    else throw PermissionException(s"""Device ${toDeviceStub().toString} does not belong to user ${device.toSimpleUser.toString}""")
  }

  /**
    * Return true if the device is part of the user OWN_DEVICES group
    */
  def deviceBelongsToUser(device: MemberResourceRepresentation): Boolean = {
    device.resource.getOwners().exists(u => u.getId.equalsIgnoreCase(representation.getId))
  }

  def getAccountInfo: UserAccountInfo = {
    val userRoles = resource.getRoles
    val groups = resource.getAllGroups()
    val wasFullyCreated = fullyCreate(Some(userRoles), Some(groups))
    val ownDeviceGroupRepresentation: GroupRepresentation = if (wasFullyCreated) { // if the user was not fully created beforehand, we need to do a full query on the user's groups
      groups.find { group =>
        group.getName.contains(Elements.PREFIX_OWN_DEVICES)
      }.get
    } else {
      resource.getAllGroups().find { group =>
        group.getName.contains(Elements.PREFIX_OWN_DEVICES)
      }.get
    }
    val ownDeviceGroupResource = GroupFactory.getById(ownDeviceGroupRepresentation.getId)
    val numberOfDevices = ownDeviceGroupResource.members().size() - 1
    val isAdmin = userRoles.exists(_.getName == Elements.ADMIN)
    UserAccountInfo(toSimpleUser, numberOfDevices, isAdmin)
  }

  /**
    * Fully create a user will
    * - give him the role USER if it already doesn't exist
    * - create him the group OWN_DEVICES
    * will fail if the user has the role DEVICE
    * @param maybeRoles
    * @param maybeAllGroups
    * @return
    */
  def fullyCreate(maybeRoles: Option[List[RoleRepresentation]] = None, maybeAllGroups: Option[List[GroupRepresentation]] = None): Boolean = synchronized {
    val userRoles = maybeRoles.getOrElse(resource.getRoles)
    val groups = resource.getAllGroups(maybeAllGroups)
    val realm = Util.getRealm
    var wasAlreadyFullyCreated = true
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
      wasAlreadyFullyCreated = false
    }

    if (!doesUserHasOwnDeviceGroup) {
      val userGroup = GroupFactory.createUserDeviceGroupQuick(representation.getUsername)
      resource.joinGroup(userGroup.representation.getId)
      wasAlreadyFullyCreated = false
    }
    wasAlreadyFullyCreated
  }

  def getOwnDeviceGroup(maybeGroups: Option[List[GroupRepresentation]] = None): GroupResource = {
    val groupRepresentation = resource.getAllGroups(maybeGroups).find { group =>
      group.getName.contains(Elements.PREFIX_OWN_DEVICES)
    }.get
    GroupFactory.getById(groupRepresentation.getId)
  }

  def changePassword(newPassword: String): Unit = {
    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(newPassword)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    resource.resetPassword(deviceCredential)
  }

  def updateDevice(deviceUpdateStruct: DeviceFE): MemberResourceRepresentation = {
    representation.setLastName(deviceUpdateStruct.description)

    val newDeviceTypeGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(deviceUpdateStruct.deviceType))

    val newOwners = deviceUpdateStruct.owner.map(o => QuickActions.quickSearchId(o.id)).map(_.toResourceRepresentation)
    changeOwnersOfDevice(newOwners)

    representation.setAttributes(deviceUpdateStruct.attributes.map { kv => kv._1 -> kv._2.asJava }.asJava)

    resource.update(representation)

    val ownersGroups = resource.getOwners().map { u => u.toResourceRepresentation.getOwnDeviceGroup().toRepresentation.getName }
    val excludedGroupNames: List[String] = ownersGroups :+ resource.getAllGroups().filter(p => p.getName.contains(Elements.PREFIX_API)).head.getName // that's resource API group
    leaveOldGroupsJoinNewGroups(deviceUpdateStruct.groups.map(_.name) :+ newDeviceTypeGroup.representation.getName, excludedGroupNames)
    getUpdatedMember
  }

  def getUpdatedMember: MemberResourceRepresentation = {
    val newResource = QuickActions.quickSearchId(representation.getId)
    MemberResourceRepresentation(newResource, newResource.toRepresentation)
  }

  protected[keycloak] def changeOwnersOfDevice(newOwners: List[MemberResourceRepresentation]): Unit = {

    if (newOwners.isEmpty) throw new InternalApiException("new owner list can not be empty")

    val oldOwners: List[MemberResourceRepresentation] = Try(resource.getOwners().map(_.toResourceRepresentation)).getOrElse(Nil)

    val ownersThatStay = newOwners.intersect(oldOwners)
    val ownersToRemove = oldOwners.filter(u => !ownersThatStay.contains(u))
    val ownersToAdd = newOwners.filter(u => !ownersThatStay.contains(u))

    ownersToRemove foreach { u => leaveGroup(u.getOwnDeviceGroup().toRepresentation) }
    ownersToAdd foreach { u => joinGroupById(u.getOwnDeviceGroup().toRepresentation.getId) }

  }

  def leaveGroup(group: GroupRepresentation): Unit = {
    if (isMemberPartOfGroup(group)) {
      try {
        resource.leaveGroup(group.getId)
      } catch {
        case e: Exception => throw e
      }
    } else {
      throw new InternalApiException(s"User with id ${representation.getId} is not part of the group with id ${group.getId}")
    }
  }

  private def isMemberPartOfGroup(group: GroupRepresentation, maybeGroups: Option[List[GroupRepresentation]] = None): Boolean = {
    resource.getAllGroups(maybeGroups).exists(g => g.getName.equalsIgnoreCase(group.getName))
  }

  def joinGroupById(groupId: String): Unit = resource.joinGroup(groupId)

  private def leaveOldGroupsJoinNewGroups(newGroups: List[String], excludedGroups: List[String]): Unit = {
    leaveAllGroupExceptSpecified(excludedGroups)
    joinNewGroupsByName(newGroups)
  }

  def joinNewGroupsByName(newGroups: List[String]): Unit = {
    newGroups foreach { newGroup =>
      joinGroupById(GroupFactory.getByName(newGroup).representation.getId)
    }
  }

  private def leaveAllGroupExceptSpecified(groupToKeep: List[String], maybeGroups: Option[List[GroupRepresentation]] = None): Unit = {
    resource.getAllGroups(maybeGroups) foreach { group =>
      if (!groupToKeep.contains(group.getName)) {
        leaveGroup(group)
      }
    }
  }

  /**
    * Attempt to make the keycloak member leave the groups contained in the groupsToLeave list.
    * If a group is not found in the list of the keycloak user's group, then nothing happens for this particular group
    * @param groupsToLeave List of the group name that the device will attempt to quit.
    * @param maybeAllGroups If provided, will use this list of groups instead of querying for fresh ones
    *                       (can be used to reduce the amount of queries against the backend).
    */
  private def leaveSpecifiedGroups(groupsToLeave: List[String], maybeAllGroups: Option[List[GroupRepresentation]] = None): Unit = {
    resource.getAllGroups(maybeAllGroups) foreach { group =>
      if (groupsToLeave.map(_.toLowerCase()).contains(group.getName.toLowerCase())) {
        leaveGroup(group)
      }
    }
  }

  def getOrCreateFirstClaimedGroup: GroupResourceRepresentation = {
    GroupFactory.getOrCreateGroup(Util.getUserFirstClaimedName(representation.getUsername))
  }

  /**
    * Claim a device and put it as its own
    * This will:
    * - Add a timestamp FIRST_CLAIMED_DATE (epoch ms) to the device
    * - Remove the device from the UNCLAIMED group (if it was already removed, throw and error)
    * - Change its description
    * - add it to the user_FIRST_CLAIMED devices
    */
  def claimDevice(secIndex: String, prefix: String, tags: List[String], namingConvention: String, newDescription: String): Unit = {

    val device = DeviceFactory.getBySecondaryIndex(secIndex, namingConvention).toResourceRepresentation
    device.resource.stopIfDeviceAlreadyClaimed
    val trans = new ClaimTransaction(device, prefix, tags, this, newDescription)
    // get a copy of all that is necessary
    trans.doKeycloakUpdate()

  }

  /**
    * Calling this road will:
    * - Check that the device is claimed and owned by the user making the request
    * - Remove it from groups (OWN_DEVICES_user, FIRST_CLAIMED_user, CLAIMED_provider)
    * - Add it to the group UNCLAIMED_DEVICES
    * - Remove relevant device attributes: FIRST_CLAIMED_TIMESTAMP, claiming_tags
    */
  def unclaimDevice(): MemberResourceRepresentation = {

    /**
      * @return The list of groups that are needed to leave when a device is unclaimed.
      */
    def groupsToLeaveWhenUnclaiming: List[Option[String]] = {
      val groupsOfTheDevice: List[GroupRepresentation] = resource.getAllGroups()
      val providerGroup = resource.getClaimedProviderGroup(Some(groupsOfTheDevice))
      val firstClaimedGroup = groupsOfTheDevice.find(g => g.getName.take(Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX.length).equalsIgnoreCase(Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX))
      val ownDevicesGroup = resource
        .getOwners(Some(groupsOfTheDevice))
        .map(_.toResourceRepresentation.getOwnDeviceGroup().toRepresentation.getName)

      firstClaimedGroup.map(_.getName) :: providerGroup.map(_.getName) :: ownDevicesGroup.map(g => Option(g))
    }

    /**
      * @return The group that the device need to join when being unclaimed
      */
    def groupToJoinWhenClaiming: GroupFE = {
      val unclaimedDeviceGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME).toGroupFE
      unclaimedDeviceGroup
    }

    def updateDeviceStructureUnclaim(deviceFE: DeviceFE, groupToJoin: GroupFE) = {
      deviceFE.removeFromAttributes(List(Elements.FIRST_CLAIMED_TIMESTAMP, Elements.CLAIMING_TAGS_NAME))
        .addGroup(groupToJoin)
    }

    val groupsToLeave: List[Option[String]] = groupsToLeaveWhenUnclaiming
    val groupToJoin: GroupFE = groupToJoinWhenClaiming

    val newDeviceStructure: DeviceFE = updateDeviceStructureUnclaim(toDeviceFE(), groupToJoin)

    val updatedDevice: MemberResourceRepresentation = updateDevice(newDeviceStructure)
    updatedDevice.leaveSpecifiedGroups(groupsToLeave.flatten)
    updatedDevice
  }

  /**
    * Return a future of a DeviceCreationState. Create a device by an administrator. Assume that the user being an administrator has already been checked.
    * Device creation differs from the normal way by:
    * - Not adding the device to the owner_OWN_DEVICES group
    * - Adding the device to the provider_PROVIDER_DEVICES
    * - Adding the device to the UNCLAIMED_DEVICES group
    * @param addDevice description of the device
    * @param provider name of the provider
    */
  def createDeviceAdminAsync(addDevice: AddDevice, provider: String)(implicit ec: ExecutionContext): Future[DeviceCreationState] = {
    Future {
      createNewDeviceAdmin(addDevice, provider)
      DeviceCreationSuccess(addDevice.hwDeviceId)
    }.recover {
      case e: WebApplicationException =>
        DeviceCreationFail(addDevice.hwDeviceId, e.getMessage, 666)
      case e: InternalApiException =>
        DeviceCreationFail(addDevice.hwDeviceId, e.getMessage, e.errorCode)
    }
  }

  def createNewDeviceAdmin(device: AddDevice, provider: String): UserResource = {
    (Util.getMember(device.hwDeviceId), Util.getMemberSecondaryIndex(device.secondaryIndex)) match {
      case (None, None) =>
        DeviceFactory.createDeviceAdmin(device, provider)
      case (Some(member), Some(_)) =>
        logger.debug(s"member with username: ${device.hwDeviceId} already exists. skip creation.")
        member.getUpdatedResource
      case (Some(_), None) => throw new InternalApiException(s"data is inconsistent. found member, but secondaryIndex doesn't exist with. username: ${device.hwDeviceId}, secondaryIndex: ${device.secondaryIndex}")
      case (None, Some(_)) => throw new InternalApiException(s"data is inconsistent. found secondaryIndex, but member doesn't exist with. username: ${device.hwDeviceId}, secondaryIndex: ${device.secondaryIndex}")
    }
  }

  def createDeviceWithIdentityCheck(device: AddDevice, claims: Claims): Try[DeviceCreationState] = synchronized {
    for {
      _ <- if (claims.hasMaybeGroups) Success(true) else Failure(InvalidClaimException("Invalid Groups", "No groups found"))
      deviceToAdd <- Try(device.copy(listGroups = claims.targetGroups.left.map(_.map(_.toString)).merge))
      uuidInContent <- Try(UUID.fromString(deviceToAdd.hwDeviceId))
        .recoverWith { case e: Exception => Failure(InvalidClaimException("Invalid UUID", e.getMessage)) }
      _ <- claims.validateIdentity(uuidInContent)
        .recoverWith { case e: Exception => Failure(InvalidClaimException("Invalid identity", e.getMessage)) }
      createdDevice <- createDevice(deviceToAdd)
    } yield {
      createdDevice
    }
  }

  def createDevice(device: AddDevice): Try[DeviceCreationState] = synchronized {
    Try {
      val resource = DeviceFactory.createDevice(device, this)
      DeviceCreationSuccess(device.hwDeviceId, Option(resource))
    }.recover {
      case e: WebApplicationException => DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
      case e: InternalApiException => DeviceCreationFail(device.hwDeviceId, e.getMessage, e.errorCode)
      case e: Exception => DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
    }

  }

  def createMultipleDevices(devices: List[AddDevice]): Future[List[DeviceCreationState]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val processOfFutures: List[Future[DeviceCreationState]] = devices.map { device =>
      Future {
        DeviceFactory.createDevice(device, this)
        DeviceCreationSuccess(device.hwDeviceId)
      }.recover {
        case e: WebApplicationException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
        case e: InternalApiException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, e.errorCode)
        case e: Exception =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
      }
    }

    val futureProcesses: Future[List[DeviceCreationState]] =
      Future.sequence(processOfFutures)

    futureProcesses.recover {
      case _ => List.empty[DeviceCreationState]
    }
  }

  def getPartialGroups: List[GroupResourceRepresentation] = resource.getAllGroups().filter { group =>
    !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API) || group.getName.contains(Elements.PREFIX_OWN_DEVICES))
  }.map(_.toResourceRepresentation)

  /**
    * When a new device is created, get which password should be his:
    * 1 If the user belongs to a DEFAULT_PASSWORD group, select the password defined in the group's attributes
    * 2 If not, get the user's DEFAULT_DEVICE_PASSWORD attribute
    * If it doesn't exist, create a random one
    */
  def getPasswordForDevice(maybeAllGroups: Option[List[GroupRepresentation]] = None): String = synchronized {
    val maybeDefaultPasswordGroup = resource.getAllGroups(maybeAllGroups).find(g => g.getName.startsWith(Elements.DEFAULT_PASSWORD_GROUP_PREFIX))
    maybeDefaultPasswordGroup match {
      case Some(group) =>
        // cast to groupResourceRepresentation in order to have all the attributes
        val nGroup = group.toResourceRepresentation
        nGroup.getAttributesScala.get(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE) match {
          case Some(value) => value.head
          case None => getUserPasswordForDevice
        }
      case None => getUserPasswordForDevice
    }
  }

  private def getUserPasswordForDevice: String = {
    val userAttributes = getAttributesScala
    userAttributes.get(Elements.DEFAULT_PASSWORD_USER_ATTRIBUTE) match {
      case Some(password) => password.head
      case None =>
        // generate random password
        UUID.randomUUID().toString
    }
  }

}

case class GroupResourceRepresentation(resource: GroupResource, representation: GroupRepresentation)(implicit realmName: String) extends LazyLogging {

  import BareKeycloakUtil._

  def getAttributesScala: Map[String, List[String]] = {
    Option(representation.getAttributes) match {
      case Some(value) => Util.attributesToMap(value)
      case None => Util.attributesToMap(resource.toRepresentation.getAttributes)
    }
  }

  def toGroupFE: GroupFE = representation.toGroupFE

  def getUpdatedGroup: GroupResourceRepresentation = GroupFactory.getById(representation.getId).toResourceRepresentation

  def deleteGroup(): Unit = {

    if (representation.getId == null || representation.getId == "") throw GroupNotFound(s"Group doesn't exist")

    if (!resource.isEmpty) throw GroupNotEmpty(s"Group with id $representation.getId is not empty")
    if (representation.getName.contains(Elements.PREFIX_OWN_DEVICES))
      throw new InternalApiException(
        s"Group with id $representation.getId is a user group with name $representation.getName"
      )
    else {
      resource.remove()
    }
  }

  def getMembers: List[UserRepresentation] = resource.getMembers

  val id: String = representation.getId
  val name: String = representation.getName

}

