package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{BadOwner, InternalApiException, PermissionException, UserNotFound}
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Users._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{AddDevice, Device, Elements, Group}
import javax.ws.rs.WebApplicationException
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Devices extends ConfigBase {

  /*
  Update a device by:
  - Removing all its attributes and replacing them by the new ones
  - Updating its type
  - Changing the owner if need be (and thus leaving old user device group / joining new one)
   */
  def updateDevice(newOwnerId: String, deviceStruct: AddDevice, deviceConfig: String, apiConfig: String)(implicit realmName: String): Device = {
    val device = Utils.getKCMemberFromUsername(deviceStruct.hwDeviceId)
    val deviceRepresentation = device.toRepresentation
    deviceRepresentation.setLastName(deviceStruct.description)

    val newDeviceTypeGroup = Groups.getTypeOfDeviceGroup(deviceStruct.deviceType)

    val oldOwnerRepresentation = getOwnerOfDevice(device.toRepresentation.getUsername).toRepresentation
    if (newOwnerId != oldOwnerRepresentation.getId) {
      changeOwnerOfDevice(deviceRepresentation.getId, newOwnerId, oldOwnerRepresentation.getId)
    }

    val deviceAttributes = Map(
      "attributesDeviceGroup" -> List(deviceConfig).asJava,
      "attributesApiGroup" -> List(apiConfig).asJava
    ).asJava

    deviceRepresentation.setAttributes(deviceAttributes)

    device.update(deviceRepresentation)

    val excludedGroups: List[String] = List(Elements.PREFIX_OWN_DEVICES, Elements.PREFIX_API)
    leaveAndJoinGroups(device, deviceStruct.listGroups :+ newDeviceTypeGroup.getId, excludedGroups)
    Devices.getDeviceByInternalKcId(deviceRepresentation.getId)
  }

  /**
    * Create de device, returns the KC ID of the device.
    *
    * @param ownerId   KeyCloak id of the owner of the device.
    * @param device    Description of the device that will be added.
    * @param realmName Name of the realm.
    * @return Id of the newly created device.
    */
  def createDevice(ownerId: String, device: AddDevice)(implicit realmName: String): String = {
    val realm = getRealm

    // check if device already exists
    try {
      Utils.getKCMemberFromUsername(device.hwDeviceId)
      throw new InternalApiException(s"device with hwDeviceId: ${device.hwDeviceId} already exists")
    } catch {
      case _: UserNotFound =>
    }

    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setEnabled(true)
    deviceRepresentation.setUsername(device.hwDeviceId)
    if (!device.description.equals("")) {
      deviceRepresentation.setLastName(device.description)
    } else deviceRepresentation.setLastName(device.hwDeviceId)
    deviceRepresentation.setFirstName(Elements.DEFAULT_FIRST_NAME)

    // groups
    val userOwnDeviceGroup = getUserOwnDevicesGroup(ownerId)
    val apiConfigGroup = getGroupByName(realmName + Elements.PREFIX_API + "default", x => x)
    val deviceConfigGroup = getGroupByName(Elements.PREFIX_DEVICE_TYPE + device.deviceType, x => x)
    val apiConfigGroupAttributes = apiConfigGroup.getAttributes.asScala.toMap
    val deviceConfigGroupAttributes = deviceConfigGroup.getAttributes.asScala.toMap

    // set attributes and credentials
    setCredential(deviceRepresentation, apiConfigGroupAttributes)
    val allAttributes = (apiConfigGroupAttributes ++ deviceConfigGroupAttributes).asJava
    deviceRepresentation.setAttributes(allAttributes)

    // create device in KC
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    val deviceKc = Utils.getKCMemberFromId(deviceKcId)
    // set role DEVICE
    val roleDevice = realm.roles().get(Elements.DEVICE).toRepresentation
    addRoleToMember(deviceKc, roleDevice)

    // join groups
    val allGroupIds = device.listGroups :+ apiConfigGroup.getId :+ deviceConfigGroup.getId :+ userOwnDeviceGroup.id
    allGroupIds foreach { groupId => addSingleUserToGroup(groupId, deviceKcId) }
    deviceKcId
  }

  /**
    * Asynchronous creation of devices. Call the createDevice method.
    *
    * @param ownerId   Id of the owner of the device.
    * @param devices   List of device representation that will be added.
    * @param realmName Name of the realm on which the devices will be added.
    * @return List of device creation success or fail.
    */
  def bulkCreateDevice(ownerId: String, devices: List[AddDevice])(implicit realmName: String): List[String] = {
    val processOfFutures = scala.collection.mutable.ListBuffer.empty[Future[String]]
    import scala.concurrent.ExecutionContext.Implicits.global
    devices.foreach { device =>
      val process = Future(try {
        createDevice(ownerId, device)
        createSuccessDevice(device.hwDeviceId)
      } catch {
        case e: WebApplicationException => createErrorDevice(device.hwDeviceId, e.getMessage)
        case e: InternalApiException => createErrorDevice(device.hwDeviceId, e.getMessage)
      })
      processOfFutures += process
    }

    val futureProcesses: Future[ListBuffer[String]] = Future.sequence(processOfFutures)

    futureProcesses.onComplete {
      case Success(success) =>
        success
      case Failure(error) =>
        throw error
        scala.collection.mutable.ListBuffer.empty[Future[String]]
    }

    Await.result(futureProcesses, conf.getInt("core.timeToWaitDevices") second).toList
  }

  private def setCredential(deviceRepresentation: UserRepresentation, apiConfigGroupAttributes: Map[String, java.util.List[String]]): Unit = {
    def extractPwd(jsonStr: String): String = {
      implicit val formats: DefaultFormats.type = DefaultFormats
      val json = parse(jsonStr)
      (json \ "password").extract[String]
    }

    val pwdDevice = extractPwd(apiConfigGroupAttributes.head._2.asScala.head)

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(pwdDevice)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))
  }

  def getSingleDeviceFromUser(deviceHwId: String, userName: String)(implicit realmName: String): Device = {
    val device = getDeviceByHwDevice(deviceHwId)
    if (doesDeviceBelongToUser(device.id, userName)) device else throw PermissionException(s"Device with hwDeviceId $deviceHwId does not belong to user $userName")
  }

  /*
  Return a FrontEndStruct.Device element based on the deviceHwId of the device (its keycloak username)
   */
  private[operations] def getDeviceByHwDevice(deviceHwId: String)(implicit realmName: String): Device = {
    Utils.getMemberByUsername(deviceHwId, completeDevice)
  }

  /*
  Return a FrontEndStruct.Device element based on the description of the device (its keycloak last name)
 */
  def getDeviceByDescription(description: String)(implicit realmName: String): Device = {
    Utils.getMemberByOneName(description, completeDevice)
  }

  def getDeviceByInternalKcId(internalKCId: String)(implicit realmName: String): Device = {
    Utils.getMemberById(internalKCId, completeDevice)
  }

  def searchMultipleDevices(searchThing: String, username: String)(implicit realmName: String): List[Device] = {
    val members = Utils.getMemberByOneName(searchThing, completeDevices, 10000)
    members filter { d => doesDeviceBelongToUser(d.id, username) }
  }

  def deleteDevice(username: String, hwDeviceId: String)(implicit realmName: String): Unit = {
    val device = Utils.getKCMemberFromUsername(hwDeviceId).toRepresentation
    if (doesDeviceBelongToUser(device.getId, username)) {
      deleteUser(device.getId)
    } else throw BadOwner("device does not belong to user")
  }

  private[operations] def removeUnwantedGroupsFromDeviceStruct(device: Device): Device = {
    val interestingGroups = device.groups.filter { group =>
      !(group.name.contains(Elements.PREFIX_DEVICE_TYPE) || group.name.contains(Elements.PREFIX_API))
    }
    Device(device.id, device.hwDeviceId, device.description, device.owner, interestingGroups, device.attributes, device.deviceType)
  }

  private[operations] def createErrorDevice(hwDeviceId: String, error: String): String = {
    val jsonError =
      hwDeviceId ->
        ("state" -> "notok") ~
        ("error" -> error)
    compact(render(jsonError))
  }

  private[operations] def createSuccessDevice(hwDeviceId: String): String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._

    val jsonOk =
      hwDeviceId ->
        ("state" -> "ok")
    compact(render(jsonOk))
  }

  private[operations] def doesDeviceBelongToUser(deviceKcId: String, userName: String)(implicit realmName: String): Boolean = {
    val groupsOfTheDevice: List[Group] = getAllGroupsOfAMember(deviceKcId)
    groupsOfTheDevice find { group => group.name.equalsIgnoreCase(Elements.PREFIX_OWN_DEVICES + userName) } match {
      case None => false
      case _ => true
    }
  }

  private[operations] def getOwnerOfDevice(hwDeviceId: String)(implicit realmName: String): UserResource = {
    val device = Utils.getKCMemberFromUsername(hwDeviceId)
    val groupsOfTheDevice = device.groups().asScala.toList
    val userGroup = groupsOfTheDevice.find { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) } match {
      case Some(value) => value
      case None => throw new InternalApiException(s"No owner defined for device $hwDeviceId")
    }
    val usernameOfDeviceOwner = userGroup.getName.split(Elements.PREFIX_OWN_DEVICES)(1)
    Utils.getKCMemberFromUsername(usernameOfDeviceOwner)
  }

  private[operations] def leaveAllGroupExceptSpecified(device: UserResource, excludedGroups: List[String])(implicit realmName: String): Unit = {
    val groups = device.groups().asScala.toList

    @tailrec
    def isGroupToBeRemoved(listGroupNames: List[String], it: String): Boolean = {
      listGroupNames match {
        case Nil => true
        case group :: restOfGroups => if (it.contains(group)) false else isGroupToBeRemoved(restOfGroups, it)
      }
    }

    groups foreach { group =>
      if (isGroupToBeRemoved(excludedGroups, group.getName)) {
        device.leaveGroup(group.getId)
      }
    }
  }

  /*
  Helper for updateDevice
   */
  private[operations] def leaveAndJoinGroups(device: UserResource, newGroups: List[String], excludedGroups: List[String])(implicit realmName: String): Unit = {
    leaveAllGroupExceptSpecified(device, excludedGroups)
    newGroups foreach { newGroup => addSingleUserToGroup(newGroup, device.toRepresentation.getId) }
  }

  /*
  Remove device from oldowner_OWN_DEVICES group
  Add device to newowner_OWN_DEVICES group
 */
  private[operations] def changeOwnerOfDevice(deviceId: String, newOwnerId: String, oldOwnerId: String)(implicit realmName: String): Unit = {
    val oldOwnerGroup = Users.getUserOwnDevicesGroup(oldOwnerId)
    val newOwnerGroup = Users.getUserOwnDevicesGroup(newOwnerId)
    Groups.leaveGroup(deviceId, oldOwnerGroup.id)
    Groups.addSingleUserToGroup(newOwnerGroup.id, deviceId)
  }

  private[operations] def getDeviceType(deviceKeyCloakId: String)(implicit realmName: String): String = {
    val groups = getGroupsOfAUser(deviceKeyCloakId)
    groups.find { group => group.name.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => group.name.split(Elements.PREFIX_DEVICE_TYPE)(1)
      case None => throw new InternalApiException(s"Device with Id $deviceKeyCloakId has no type")
    }
  }

}
