package com.ubirch.webui.models.keycloak.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ BadOwner, BadRequestException, DeviceAlreadyClaimedException, GroupNotEmpty, GroupNotFound, InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member._
import javax.ws.rs.WebApplicationException
import org.keycloak.admin.client.resource.{ GroupResource, UserResource }
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

package object BareKeycloakUtil {

  implicit class RichUserResource(val userResource: UserResource) {

    def stopIfDeviceAlreadyClaimed(implicit realmName: String): Unit = if (userResource.isClaimed()) throw DeviceAlreadyClaimedException(s"Device already claimed by ${this.getOwners().map(_.getUsername).mkString(", ")}")

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
    def isUserAuthorized(user: UserRepresentation)(implicit realmName: String): Boolean = {
      getOwners().exists(u => u.getId.equalsIgnoreCase(user.getId))
    }

    def getAllGroups(maybeAllGroups: Option[List[GroupRepresentation]] = None): List[GroupRepresentation] =
      maybeAllGroups.getOrElse(userResource.groups().asScala.toList)

    def getRoles: List[RoleRepresentation] = userResource.roles().realmLevel().listEffective().asScala.toList

    def getDeviceType(maybeAllGroups: Option[List[GroupRepresentation]] = None): String = {
      userResource.getDeviceGroup(maybeAllGroups) match {
        case Some(group) => group.getName.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
        case None => throw new InternalApiException(s"Device with Id ${userResource.toRepresentation.getId} has no type")
      }
    }

    def getDeviceGroup(maybeAllGroups: Option[List[GroupRepresentation]] = None): Option[GroupRepresentation] = userResource.getAllGroups(maybeAllGroups).find { group => group.getName.contains(Elements.PREFIX_DEVICE_TYPE) }

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

    def addRoles(roles: List[RoleRepresentation]) = {
      userResource.roles().realmLevel().add(roles.asJava)
    }

    def toResourceRepresentation(implicit realmName: String): MemberResourceRepresentation = {
      MemberResourceRepresentation(userResource, userResource.toRepresentation)
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

    def getOwnDeviceGroup(maybeAllGroups: Option[List[GroupRepresentation]] = None): GroupRepresentation = {
      userResource.getAllGroups(maybeAllGroups).find { group =>
        group.getName.contains(Elements.PREFIX_OWN_DEVICES)
      }.get
    }

    def getUpdatedResource(implicit realmName: String): UserResource = QuickActions.quickSearchId(userResource.toRepresentation.getId)

    def isAdmin: Boolean = getRoles.exists(_.getName == Elements.ADMIN)

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
        correctNumberOfDevices.sortBy(_.representation.getUsername) map (d => DeviceStub(
          hwDeviceId = d.representation.getUsername,
          description = d.representation.getLastName,
          deviceType = d.getType()
        ))
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
      Converter.attributesToMap(resource.toRepresentation.getAttributes)
    } else {
      Converter.attributesToMap(representation.getAttributes)
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
      device.joinGroup(group.getId)
    } else throw PermissionException("User cannot add device to group")
  }

  def canUserAddDeviceToGroup(device: MemberResourceRepresentation): Boolean = {
    if (!device.resource.isDevice)
      throw new InternalApiException("The device is not a device")
    if (resource.isDevice)
      throw new InternalApiException("The user is not a user in KC")
    device.resource.getOwners().exists(u => u.getId.equalsIgnoreCase(representation.getId))
  }

  def toDeviceFE: DeviceFE = {
    val t0 = System.currentTimeMillis()
    val allGroupsRepresentation = resource.getAllGroups()

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
      attributes = getAttributesScala,
      deviceType = resource.getDeviceType(Some(allGroupsRepresentation)),
      created = representation.getCreatedTimestamp.toString
    )

    logger.debug(s"~~ Time to toDeviceFE = ${System.currentTimeMillis() - t0}ms")
    res
  }

  def toAddDevice: AddDevice = {
    val deviceFE = toDeviceFE
    AddDevice(
      hwDeviceId = deviceFE.hwDeviceId,
      description = deviceFE.description,
      deviceType = deviceFE.deviceType,
      listGroups = resource.getAllGroups().map { g => g.getName },
      attributes = deviceFE.attributes,
      secondaryIndex = this.getSecondaryIndex
    )
  }

  def toDeviceDumb: DeviceDumb = {
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
      deviceType = resource.getDeviceType(None)
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

  def ifUserAuthorizedReturnDeviceFE(user: UserRepresentation): DeviceFE = {
    val owners = resource.getOwners()
    logger.debug("owners: " + owners.map { u => u.getUsername }.mkString(", "))
    if (owners.exists(u => u.getId.equalsIgnoreCase(user.getId))) this.toDeviceFE
    else throw PermissionException(s"""Device ${toDeviceStub.toString} does not belong to user ${user.toSimpleUser.toString}""")
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
      val userGroupId = GroupFactory.createUserDeviceGroupQuick(representation.getUsername)
      resource.joinGroup(userGroupId)
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
    ownersToAdd foreach { u => joinGroup(u.getOwnDeviceGroup().toRepresentation.getId) }

  }

  def leaveGroup(group: GroupRepresentation): Unit = {
    if (isMemberPartOfGroup(group)) {
      try {
        resource.leaveGroup(group.getId)
      } catch {
        case e: Exception => throw e
      }
    } else {
      throw new InternalApiException(
        s"User with id ${representation.getId} is not part of the group with id ${group.getId}"
      )
    }
  }

  private def isMemberPartOfGroup(group: GroupRepresentation, maybeGroups: Option[List[GroupRepresentation]] = None): Boolean = {
    resource.getAllGroups(maybeGroups).exists(g => g.getName.equalsIgnoreCase(group.getName))
  }

  def joinGroup(groupId: String): Unit = resource.joinGroup(groupId)

  private def leaveOldGroupsJoinNewGroups(newGroups: List[String], excludedGroups: List[String]): Unit = {
    leaveAllGroupExceptSpecified(excludedGroups)
    joinNewGroups(newGroups)
  }

  private def joinNewGroups(newGroups: List[String]): Unit = {
    newGroups foreach { newGroup =>
      joinGroup(GroupFactory.getByName(newGroup).representation.getId)
    }
  }

  private def leaveAllGroupExceptSpecified(groupToKeep: List[String], maybeGroups: Option[List[GroupRepresentation]] = None): Unit = {
    resource.getAllGroups(maybeGroups) foreach { group =>
      if (!groupToKeep.contains(group.getName)) {
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
    * - add it to the user_FIRST_CLAIMED devices
    */
  def claimDevice(secIndex: String, prefix: String, tags: List[String], namingConvention: String): Unit = {

    val device = DeviceFactory.getBySecondaryIndex(secIndex, namingConvention).toResourceRepresentation
    device.resource.stopIfDeviceAlreadyClaimed
    val trans = new ClaimTransaction(device, prefix: String, tags, this)
    try {
      trans.commitImpl()
    } catch {
      case e: Exception =>
        logger.error(s"Error trying to claim thing with $namingConvention $secIndex. Rolling back")
        trans.rollbackImpl()
        throw e
    }

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
    Future(try {
      createNewDeviceAdmin(addDevice, provider)
      DeviceCreationSuccess(addDevice.hwDeviceId)
    } catch {
      case e: WebApplicationException =>
        DeviceCreationFail(addDevice.hwDeviceId, e.getMessage, 666)
      case e: InternalApiException =>
        DeviceCreationFail(addDevice.hwDeviceId, e.getMessage, e.errorCode)
    })
  }

  def createNewDeviceAdmin(device: AddDevice, provider: String): UserResource = {
    DeviceFactory.createDeviceAdmin(device, provider)
  }

  def createMultipleDevices(devices: List[AddDevice]): Future[List[DeviceCreationState]] = {
    val processOfFutures =
      scala.collection.mutable.ListBuffer.empty[Future[DeviceCreationState]]
    import scala.concurrent.ExecutionContext.Implicits.global
    devices.foreach { device =>
      val process = Future(try {
        DeviceFactory.createDevice(device, resource)
        DeviceCreationSuccess(device.hwDeviceId)
      } catch {
        case e: WebApplicationException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
        case e: InternalApiException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, e.errorCode)
        case e: Exception =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
      })
      processOfFutures += process
    }
    val futureProcesses: Future[ListBuffer[DeviceCreationState]] =
      Future.sequence(processOfFutures)

    futureProcesses.onComplete {
      case Success(success) =>
        success
      case Failure(_) =>
        scala.collection.mutable.ListBuffer.empty[Future[DeviceCreationState]]
    }

    futureProcesses.map(_.toList)

  }

  def getPartialGroups: List[GroupResourceRepresentation] = resource.getAllGroups().filter { group =>
    !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API) || group.getName.contains(Elements.PREFIX_OWN_DEVICES))
  }.map(_.toResourceRepresentation)

}

case class GroupResourceRepresentation(resource: GroupResource, representation: GroupRepresentation)(implicit realmName: String) extends LazyLogging {

  import BareKeycloakUtil._

  def getAttributesScala: Map[String, List[String]] = {
    if (Option(representation.getAttributes).isEmpty) {
      Converter.attributesToMap(resource.toRepresentation.getAttributes)
    } else {
      Converter.attributesToMap(representation.getAttributes)
    }
  }

  def toGroupFE = representation.toGroupFE

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

