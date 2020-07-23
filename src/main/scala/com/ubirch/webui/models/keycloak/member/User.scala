package com.ubirch.webui.models.keycloak.member

import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.Exceptions.{ BadOwner, InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak.group.{ Group, GroupFactory }
import com.ubirch.webui.models.keycloak.util.{ Converter, Util }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.keycloak.{ AddDevice, DeviceStub, SimpleUser, UserAccountInfo }
import javax.ws.rs.WebApplicationException
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class User(keyCloakMember: UserResource)(implicit realmName: String) extends Member(keyCloakMember) with ConfigBase {

  override def getGroups: List[Group] =
    super.getGroups
      .filter(group => !group.name.contains(Elements.PREFIX_OWN_DEVICES))

  def createMultipleDevices(devices: List[AddDevice]): List[DeviceCreationState] = {
    val processOfFutures =
      scala.collection.mutable.ListBuffer.empty[Future[DeviceCreationState]]
    import scala.concurrent.ExecutionContext.Implicits.global
    devices.foreach { device =>
      val process = Future(try {
        createNewDevice(device)
        DeviceCreationSuccess(device.hwDeviceId)
      } catch {
        case e: WebApplicationException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, 666)
        case e: InternalApiException =>
          DeviceCreationFail(device.hwDeviceId, e.getMessage, e.errorCode)
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

    Await.result(futureProcesses, timeToWait.second).toList

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

  def createNewDeviceAdmin(device: AddDevice, provider: String): Device = {
    DeviceFactory.createDeviceAdmin(device, provider)
  }

  def createNewDevice(device: AddDevice): Device = {
    DeviceFactory.createDevice(device, this)
  }

  /**
    * Claim a device and put it as its own
    * This will:
    * - Add a timestamp FIRST_CLAIMED_DATE (epoch ms) to the device
    * - Remove the device from the UNCLAIMED group (if it was already removed, throw and error)
    * - add it to the user_FIRST_CLAIMED devices
    */
  def claimDevice(secIndex: String, prefix: String, tags: List[String], namingConvention: String): Unit = {

    val device: Device = DeviceFactory.getBySecondaryIndex(secIndex, namingConvention)
    val trans = new ClaimTransaction(device, prefix: String, tags, this)
    device.stopIfDeviceAlreadyClaimed()
    try {
      trans.commitImpl()
    } catch {
      case e: Exception =>
        logger.error(s"Error trying to claim thing with $namingConvention $secIndex. Rolling back")
        trans.rollbackImpl()
        throw e
    }

  }

  def getOwnDevices: List[Device] = {
    getOwnDeviceGroup.getMembers.getDevices
  }

  def getOwnDevicesStub: List[DeviceStub] = {
    getOwnDeviceGroup.getMembers.getDevices.map { d =>
      d.toDeviceStub
    }
  }

  def getOrCreateFirstClaimedGroup: Group = {
    GroupFactory.getOrCreateGroup(Util.getUserFirstClaimedName(this.getUsername))
  }

  def getOwnDeviceGroup: Group = {
    getAllGroupsQuick.find { group =>
      group.getName.contains(Elements.PREFIX_OWN_DEVICES)
    } match {
      case Some(value) => Converter.groupsRepresentationToGroup(List(value)).head
      case None =>
        throw new InternalApiException(
          s"User with Id $memberId doesn't have a OWN_DEVICE group"
        )
    }
  }

  private[keycloak] def getAllGroups = super.getGroups

  private[keycloak] def getAllGroupsQuick: List[GroupRepresentation] = keyCloakMember.groups().asScala.toList

  def addDevicesToGroup(devices: List[Device], group: Group): Unit = {
    devices foreach (d => addDeviceToGroup(d, group))
  }

  def addDeviceToGroup(device: Device, group: Group): Unit = {
    if (canUserAddDeviceToGroup(device)) {
      device.joinGroup(group)
    } else throw PermissionException("User cannot add device to group")
  }

  def canUserAddDeviceToGroup(device: Device): Boolean = {
    if (!device.isMemberDevice)
      throw new InternalApiException("The device is not a device")
    if (isMemberDevice)
      throw new InternalApiException("The user is not a user in KC")
    device.getOwners.exists(u => u.isEqual(this))
  }

  def deleteOwnDevice(device: Device): Unit = {
    if (device.getOwners.exists(u => u.isEqual(this))) {
      device.deleteMember()
    } else throw BadOwner("device does not belong to user")
  }

  def fullyCreate(): Unit = synchronized {
    addUserRoleIfNotPresent()
    createDeviceGroupIfNotExisting()
  }

  def getAccountInfo: UserAccountInfo = {
    fullyCreate()
    toUserAccountInfo
  }

  def toUserAccountInfo: UserAccountInfo =
    UserAccountInfo(toSimpleUser, this.getNumberOfOwnDevices, this.isAdmin)

  def toSimpleUser: SimpleUser = {
    val userRepresentation = this.toRepresentation
    SimpleUser(
      userRepresentation.getId,
      userRepresentation.getUsername,
      userRepresentation.getLastName,
      userRepresentation.getFirstName
    )
  }

  def getNumberOfOwnDevices: Int = getOwnDeviceGroup.numberOfMembers - 1

  private def addUserRoleIfNotPresent(): Unit = {
    if (!doesUserHasUserRole) {
      addRole(realm.roles().get(Elements.USER).toRepresentation)
      logger.debug(s"added role USER to user with id $memberId")
    }
  }

  private def createDeviceGroupIfNotExisting(): Unit = synchronized {
    if (!doesUserHasDeviceGroup) {
      val userGroup = GroupFactory.createUserDeviceGroup(getUsername)
      joinGroup(userGroup)
      logger.debug(s"added group OWN_DEVICES to user val id $memberId")
    }
  }

  private def doesUserHasDeviceGroup = {
    try {
      getOwnDeviceGroup
      true
    } catch {
      case _: Throwable => false
    }
  }

  private def doesUserHasUserRole: Boolean = {
    val roles = getRoles
    if (roles.exists(r => r.getName.equalsIgnoreCase(Elements.DEVICE)))
      throw new InternalApiException(
        "user is a device OR also has the role device"
      )
    roles.exists(r => r.getName.equalsIgnoreCase(Elements.USER))
  }
}
