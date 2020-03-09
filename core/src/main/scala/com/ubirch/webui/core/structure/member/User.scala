package com.ubirch.webui.core.structure.member

import java.util.concurrent.TimeUnit

import com.google.common.base.{Supplier, Suppliers}
import com.ubirch.webui.core.Exceptions.{BadOwner, InternalApiException, PermissionException}
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.group.{Group, GroupFactory}
import javax.ws.rs.WebApplicationException
import org.keycloak.admin.client.resource.UserResource

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
      case Failure(error) =>
        throw error
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
  def claimDevice(secIndex: String, prefix: String, tags: String, namingConvention: String): Unit = {

    var t0 = System.currentTimeMillis()
    val device: Device = DeviceFactory.getBySecondaryIndex(secIndex, namingConvention)
    logger.debug(s"Time to DeviceFactory.getBySecondaryIndex(secIndex, namingConvention): ${System.currentTimeMillis() - t0}ms")
    device.stopIfDeviceAlreadyClaimed()

    t0 = System.currentTimeMillis()
    val unclaimedGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getApiConfigGroupName(realmName))
    }, 5, TimeUnit.MINUTES)

    lazy val deviceConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getDeviceConfigGroupName(device.getDeviceType))
    }, 5, TimeUnit.MINUTES)

    val apiConfigGroupAttributes = apiConfigGroup.get().getAttributes.attributes.keys.toList
    val deviceConfigGroupAttributes = deviceConfigGroup.get().getAttributes.attributes.keys.toList

    val provider = device.getProviderName

    lazy val provClaimedGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getOrCreateGroup(Util.getProviderClaimedDevicesName(provider))
    }, 5, TimeUnit.MINUTES)

    logger.debug(s"Time to get all groups: ${System.currentTimeMillis() - t0}ms")

    t0 = System.currentTimeMillis()
    device.leaveGroup(unclaimedGroup)
    logger.debug(s"Time to leave unclaimed group: ${System.currentTimeMillis() - t0}ms")

    t0 = System.currentTimeMillis()
    val addDeviceStruct = device.toAddDevice
    logger.debug(s"Time to get device toAddDevice: ${System.currentTimeMillis() - t0}ms")

    t0 = System.currentTimeMillis()
    val addDeviceStructUpdated: AddDevice = addDeviceStruct
      .addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString())))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> List(tags)))
      .removeFromAttributes(apiConfigGroupAttributes)
      .removeFromAttributes(deviceConfigGroupAttributes)
      .addGroup(getOrCreateFirstClaimedGroup.name)
      .addGroup(provClaimedGroup.get().name)
      .removeGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
      .addPrefixToDescription(prefix)
    logger.debug(s"Time to convert to addDeviceStructUpdated: ${System.currentTimeMillis() - t0}ms")

    t0 = System.currentTimeMillis()
    val res = device.updateDevice(
      newOwners = List(this),
      deviceUpdateStruct = addDeviceStructUpdated,
      deviceConfig = addDeviceStruct.attributes,
      apiConfig = addDeviceStruct.attributes
    )
    logger.debug(s"Time to update device: ${System.currentTimeMillis() - t0}ms")
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
    getAllGroups.find { group =>
      group.name.contains(Elements.PREFIX_OWN_DEVICES)
    } match {
      case Some(value) => value
      case None =>
        throw new InternalApiException(
          s"User with Id $memberId doesn't have a OWN_DEVICE group"
        )
    }
  }

  private[structure] def getAllGroups = super.getGroups

  def addDevicesToGroup(devices: List[Device], group: Group): Unit = {
    devices foreach (d => addDeviceToGroup(d, group))
  }

  def addDeviceToGroup(device: Device, group: Group): Unit = {
    if (canUserAddDeviceToGroup(device, group)) {
      device.joinGroup(group)
    } else throw PermissionException("User cannot add device to group")
  }

  def canUserAddDeviceToGroup(device: Device, group: Group): Boolean = {
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
    UserAccountInfo(toSimpleUser, this.getNumberOfOwnDevices)

  def toSimpleUser: SimpleUser = {
    val userRepresentation = this.toRepresentation
    SimpleUser(
      this.memberId,
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
