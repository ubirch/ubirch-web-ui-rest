package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{ BadOwner, InternalApiException, PermissionException, UserNotFound }
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Users._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{ AddDevice, Device, Group }
import javax.ws.rs.WebApplicationException
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object Devices extends ConfigBase {

  /*
  Update a device by:
  - Removing all its attributes and replacing them by the new ones
  - Updating its type
  - Changing the owner if need be (and thus leaving old user device group / joining new one)
   */
  def updateDevice(newOwnerId: String, deviceStruct: AddDevice, deviceConfig: String, apiConfig: String)(implicit realmName: String): Device = {
    val device = Utils.getKCUserFromUsername(deviceStruct.hwDeviceId)
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

    val excludedGroups: List[String] = List("_OWN_DEVICES", "apiConfigGroup")
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
      Utils.getKCUserFromUsername(device.hwDeviceId)
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

    // groups
    val userOwnDeviceGroup = getUserOwnDevicesGroup(ownerId)
    val apiConfigGroup = getGroupByName(s"${realmName}_apiConfigGroup_default", x => x)
    val deviceConfigGroup = getGroupByName(s"${device.deviceType}_DeviceConfigGroup", x => x)
    val apiConfigGroupAttributes = apiConfigGroup.getAttributes.asScala.toMap
    val deviceConfigGroupAttributes = deviceConfigGroup.getAttributes.asScala.toMap

    // set attributes and credentials
    setCredential(deviceRepresentation, apiConfigGroupAttributes)
    val allAttributes = (apiConfigGroupAttributes ++ deviceConfigGroupAttributes).asJava
    deviceRepresentation.setAttributes(allAttributes)

    // create device in KC
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    val deviceKc = Utils.getKCUserFromId(deviceKcId)
    // set role DEVICE
    val roleDevice = realm.roles().get("DEVICE").toRepresentation
    addRoleToUser(deviceKc, roleDevice)

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
    devices.foreach { d =>
      val process = Future(try {
        createDevice(ownerId, d)
        createSuccessDevice(d.hwDeviceId)
      } catch {
        case e: WebApplicationException => createErrorDevice(d.hwDeviceId, e.getMessage)
        case e: InternalApiException => createErrorDevice(d.hwDeviceId, e.getMessage)
      })
      processOfFutures += process
    }

    val futureProcesses: Future[ListBuffer[String]] = Future.sequence(processOfFutures)

    futureProcesses.onComplete {
      case Success(l) =>
        l
      case Failure(e) =>
        throw e
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

  def deleteDevice(username: String, hwDeviceId: String)(implicit realmName: String): Unit = {
    val device = Utils.getKCUserFromUsername(hwDeviceId).toRepresentation
    if (doesDeviceBelongToUser(device.getId, username)) {
      deleteUser(device.getId)
    } else throw BadOwner("device does not belong to user")
  }

  private[operations] def removeUnwantedGroupsFromDeviceStruct(device: Device): Device = {
    val interestingGroups = device.groups.filter { g =>
      !(g.name.contains("DeviceConfigGroup") || g.name.contains("apiConfigGroup"))
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
    val listGroupsDevice: List[Group] = getAllGroupsOfAUser(deviceKcId)
    listGroupsDevice find { g => g.name.equalsIgnoreCase(s"${userName}_OWN_DEVICES") } match {
      case None => false
      case _ => true
    }
  }

  private[operations] def getOwnerOfDevice(hwDeviceId: String)(implicit realmName: String): UserResource = {
    val device = Utils.getKCUserFromUsername(hwDeviceId)
    val groups = device.groups().asScala.toList
    val userGroup = groups.find { g => g.getName.contains(s"_OWN_DEVICES") } match {
      case Some(v) => v
      case None => throw new InternalApiException(s"No owner defined for device $hwDeviceId")
    }
    val ownerUsername = userGroup.getName.split("_OWN_DEVICES").head
    Utils.getKCUserFromUsername(ownerUsername)
  }

  private[operations] def leaveAllGroupExceptSpecified(device: UserResource, excludedGroups: List[String])(implicit realmName: String): Unit = {
    val groups = device.groups().asScala.toList

    @tailrec
    def isGroupToBeRemoved(listGroupNames: List[String], it: String): Boolean = {
      listGroupNames match {
        case Nil => true
        case x :: xs => if (it.contains(x)) false else isGroupToBeRemoved(xs, it)
      }
    }

    groups foreach { g =>
      if (isGroupToBeRemoved(excludedGroups, g.getName)) {
        device.leaveGroup(g.getId)
      }
    }
  }

  /*
  Helper for updateDevice
   */
  private[operations] def leaveAndJoinGroups(device: UserResource, newGroups: List[String], excludedGroups: List[String])(implicit realmName: String): Unit = {
    leaveAllGroupExceptSpecified(device, excludedGroups)
    newGroups foreach { ng => addSingleUserToGroup(ng, device.toRepresentation.getId) }
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

  private[operations] def getDeviceType(kcId: String)(implicit realmName: String): String = {
    val groups = getGroupsOfAUser(kcId)
    groups.find { g => g.name.contains("_DeviceConfigGroup") } match {
      case Some(g) => g.name.split("_DeviceConfigGroup").head
      case None => throw new InternalApiException(s"Device with Id $kcId has no type")
    }
  }
}
