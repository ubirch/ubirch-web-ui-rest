package com.ubirch.webui.core.operations

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{BadOwner, DeviceNotFound}
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Users._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{AddDevice, Device, Group}
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Devices {


  /**
    * Create de device, returns the KC ID of the device.
    *
    * @param ownerId   KeyCloak id of the owner of the device.
    * @param device    :     Device description to add
    * @param realmName Name of the realm.
    * @return Id of the newly created device.
    */
  def createDevice(ownerId: String, device: AddDevice)(implicit realmName: String): String = {
    val realm = getRealm
    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setUsername(device.hwDeviceId)
    if (!device.description.equals("")) {
      deviceRepresentation.setLastName(device.description)
    } else deviceRepresentation.setLastName(device.hwDeviceId)

    // get groups of the user and find the ApiConfigGroup
    val groups = getGroupsOfAUser(ownerId)
    val apiConfigGroupId = groups find { g => g.name.equals(realmName + "_apiConfigGroup_default") } match {
      case Some(v) => v.id
      case None => throw new Exception(s"No ApiConfigGroup available in this realm")
    }
    val apiConfigGroupAttributes = realm.groups().group(apiConfigGroupId).toRepresentation.getAttributes.asScala.toMap

    // get DeviceConfigurationGroup
    val allGroups = realm.groups().groups().asScala.toList
    val deviceConfigGroup = allGroups.find { g => g.getName.equals(s"${device.deviceType}_DeviceConfigGroup") } match {
      case Some(v) => v
      case None => throw new Exception(s"No ApiConfigGroup available for device type ${device.deviceType}")
    }
    val deviceConfigGroupAttributes = realm.groups().group(deviceConfigGroup.getId).toRepresentation.getAttributes.asScala.toMap

    // set attributes and credentials
    setCredential(deviceRepresentation, apiConfigGroupAttributes)
    val allAttributes = (apiConfigGroupAttributes ++ deviceConfigGroupAttributes).asJava
    deviceRepresentation.setAttributes(allAttributes)

    // create device in KC
    val res: Response = realm.users().create(deviceRepresentation)
    val deviceKcId = ApiUtil.getCreatedId(res)
    val deviceKc = getKCUserFromId(deviceKcId)
    // set role DEVICE
    val roleDevice = realm.roles().get("DEVICE").toRepresentation
    addRoleToUser(deviceKc, roleDevice)

    // join groups
    if (device.listGroups.nonEmpty) device.listGroups foreach { groupId => addSingleUserToGroup(groupId, deviceKcId) }
    addSingleUserToGroup(apiConfigGroupId, deviceKcId)
    addSingleUserToGroup(deviceConfigGroup.getId, deviceKcId)
    val userOwnDeviceGroup = getUserOwnDevicesGroup(ownerId)
    deviceKc.joinGroup(userOwnDeviceGroup.id)
    deviceKcId
  }

  def bulkCreateDevice(ownerId: String, devices: List[AddDevice])(implicit realmName: String): List[String] = {
    val processOfFutures = scala.collection.mutable.ListBuffer.empty[Future[String]]
    import scala.concurrent.ExecutionContext.Implicits.global
    devices.foreach { d =>
      val process = Future(try {
        createDevice(ownerId, d)
        createSuccessDevice(d.hwDeviceId)
      } catch {
        case e: WebApplicationException => createErrorDevice(d.hwDeviceId, e.getMessage)
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

    Await.result(futureProcesses, 1 second).toList
    /*    devices map { d =>
          try {
            createDevice(ownerId, d)
            "OK " + d.hwDeviceId
          } catch {
            case e: Exception => e.getMessage + d.hwDeviceId
          }
        }*/
  }

  private def setCredential(deviceRepresentation: UserRepresentation, apiConfigGroupAttributes: Map[String, java.util.List[String]]): Unit = {
    val pwdDevice = apiConfigGroupAttributes.get("default_password") match {
      case Some(pwd) => pwd.get(0)
      case None => throw new Exception(s"No default password for ApiConfigGroup of the realm")
    }

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(pwdDevice)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))
  }

  def getSingleDeviceFromUser(deviceHwId: String, userName: String)(implicit realmName: String): Device = {
    val deviceWithUnwantedGroups = findDeviceByHwDevice(deviceHwId)
    val device = removeUnwantedGroupsFromDevice(deviceWithUnwantedGroups)
    if (doesDeviceBelongToUser(device.id, userName)) device else throw new Exception(s"Device with hwDeviceId $deviceHwId does not belong to user $userName")
  }

  /*
  Return a FrontEndStruct.Device element based on the deviceHwId of the device (its keycloak username)
   */
  private[operations] def findDeviceByHwDevice(deviceHwId: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val deviceDb: UserRepresentation = realm.users().search(deviceHwId, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with id $deviceHwId is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb)
  }

  /*
  Return a FrontEndStruct.Device element based on the description of the device (its keycloak last name)
 */
  def findDeviceByDescription(description: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val deviceDb = realm.users().search(description, 0, 1) match {
      case null =>
        throw DeviceNotFound(s"device with description $description is not present in $realmName")
        new UserRepresentation
      case x => x.get(0)
    }
    completeDevice(deviceDb)
  }

  def findDeviceByInternalKcId(internalKCId: String)(implicit realmName: String): Device = {
    val realm = getRealm
    val device = realm.users().get(internalKCId)
    completeDevice(device.toRepresentation)
  }


  def deleteDevice(username: String, hwDeviceId: String)(implicit realmName: String): Unit = {
    val device = Utils.getKCUserFromUsername(hwDeviceId).toRepresentation
    if (doesDeviceBelongToUser(device.getId, username)) deleteUser(device.getId) else throw BadOwner("device does not belong to user")
  }

  private[operations] def doesDeviceBelongToUser(deviceKcId: String, userName: String)(implicit realmName: String): Boolean = {
    val listGroupsDevice: List[Group] = getAllGroupsOfAUser(deviceKcId)
    listGroupsDevice find { g => g.name.equals(s"${userName}_OWN_DEVICES") } match {
      case None => false
      case _ => true
    }
  }

  private[operations] def removeUnwantedGroupsFromDevice(device: Device): Device = {
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

    val jsonError =
      hwDeviceId ->
        ("state" -> "ok")
    compact(render(jsonError))
  }
}
