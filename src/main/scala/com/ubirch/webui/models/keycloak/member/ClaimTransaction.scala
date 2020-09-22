package com.ubirch.webui.models.keycloak.member

import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.DeviceFE
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, MemberResourceRepresentation, Util }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.Exceptions.{ GroupNotFound, InternalApiException }
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._

/**
  * This class is an helper used to handle a claiming of a device in a way that, if any error appears during the claiming,
  * the device will be restored to its previous state
  */
class ClaimTransaction(device: MemberResourceRepresentation, prefix: String, tags: List[String], user: MemberResourceRepresentation, newDescription: String)(implicit realmName: String) extends LazyLogging {

  val deviceGroups: List[GroupRepresentation] = device.resource.getAllGroups()
  val unclaimedDevicesGroup = getUnclaimedDeviceGroup(deviceGroups)
  val apiGroup = getApiGroup
  val deviceProviderName = getDeviceProviderName(deviceGroups)
  val claimedGroupProvider: GroupResourceRepresentation = getClaimedProviderGroup(deviceProviderName)

  val newDevicePassword = getNewDevicePassword

  val newApiAttributes: GroupAttributes = getNewApiAttributes(apiGroup, newDevicePassword)

  val oldDeviceFE = getDeviceFE(deviceGroups)

  val updatedDeviceFE: DeviceFE =
    oldDeviceFE.addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString)))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> tags))
      .removeFromAttributes(newApiAttributes.attributes.keys.toList)
      .addToAttributes(newApiAttributes.asScala)
      .addGroup(user.getOrCreateFirstClaimedGroup.representation.toGroupFE)
      .addGroup(claimedGroupProvider.representation.toGroupFE)
      .removeGroup(unclaimedDevicesGroup.toGroupFE)
      .copy(description = newDescription)
      .copy(owner = List(user.toSimpleUser))
      .addPrefixToDescription(prefix)

  /**
    * What will change:
    * GROUPS:
    *   - leave
    *     $UNCLAIMED_DEVICE_GROUP
    *   - join
    *     $user FIRST_CLAIMED_GROUP
    *     $provider CLAIMED_GROUP
    *     $OWN_DEVICES user (implicit when changing user owner)
    * ATTRIBUTES:
    *   - add
    *     $FIRST_CLAIMED_TIMESTAMP
    *     $CLAIMING_TAGS_NAME
    *   - update
    *     $api attribute (change password)
    * DESCRIPTION (last name)
    *   - change and add prefix
    */
  def doKeycloakUpdate() = {
    try {
      device.updateDevice(updatedDeviceFE)
    } catch {
      case e: Throwable =>
        val errorMessage = s"CLAIMING - doKeycloakUpdate: error while doing actual update against keycloak: ${device.toDeviceDumb}, rolling back"
        logger.error(errorMessage, e)
        device.updateDevice(oldDeviceFE)
        throw new InternalApiException(errorMessage)
    }

    try {
      device.changePassword(newDevicePassword)
    } catch {
      case e: Throwable =>
        val errorMessage = s"CLAIMING - doKeycloakUpdate: error while changing password: ${device.toDeviceDumb}, rolling back"
        logger.error(errorMessage, e)
        device.updateDevice(oldDeviceFE)
        throw new InternalApiException(errorMessage)
    }
  }

  private def rollback(): Unit = {
    try {
      device.updateDevice(oldDeviceFE)
    } catch {
      case e: Throwable =>
        logger.error("CLAIMING - rollback: error while rolling back", e)
        throw e
    }
  }

  def commitImpl(): Unit = {

    val deviceGroup = Some(device.resource.getAllGroups())
    val unclaimedDeviceGroup = GroupFactory.getByNameQuick(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
    }, 5, TimeUnit.MINUTES)

    // using the getOrCreate even though we only call the name after to make sure that the group is created
    val claimedGroupProvider = GroupFactory.getOrCreateGroup(Util.getProviderClaimedDevicesName(device.resource.getProviderName(deviceGroup)))

    //update password
    val newDevicePassword = user.getPasswordForDevice()
    val newApiAttributes = GroupAttributes(apiConfigGroup.get().representation.getAttributes.asScala.toMap).setValue("password", newDevicePassword)

    val addDeviceStruct = device.toDeviceFE()

    val addDeviceStructUpdated: DeviceFE = addDeviceStruct
      .addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString)))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> tags))
      .removeFromAttributes(newApiAttributes.attributes.keys.toList)
      .addToAttributes(newApiAttributes.asScala)
      .addGroup(user.getOrCreateFirstClaimedGroup.representation.toGroupFE)
      .addGroup(claimedGroupProvider.representation.toGroupFE)
      .removeGroup(unclaimedDeviceGroup.toGroupFE)
      .copy(description = newDescription)
      .copy(owner = List(user.toSimpleUser))
      .addPrefixToDescription(prefix)

    device.updateDevice(addDeviceStructUpdated)

    device.changePassword(newDevicePassword)

  }

  def rollbackImpl(): Unit = {
    logger.info("Rolling back data change to the claiming of device")
    var addDeviceStruct = device.toDeviceFE()
    val groups = getGroups(device)
    addDeviceStruct = putBackDeviceToUnclaimedGroup(addDeviceStruct, groups)
    addDeviceStruct = removeFromGroup(addDeviceStruct, groups)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    device.updateDevice(
      addDeviceStruct.copy(owner = List(user.toSimpleUser))
    )
  }

  def getGroups(device: MemberResourceRepresentation): GroupList = {
    GroupList(
      GroupFactory.getByNameQuick(Elements.UNCLAIMED_DEVICES_GROUP_NAME).toResourceRepresentation,
      GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation,
      device.resource.getDeviceGroup().getOrElse(throw new InternalApiException(s"Device with Id ${device.representation.getId} has no type")).toResourceRepresentation,
      user.getOwnDeviceGroup().toResourceRepresentation,
      user.getOrCreateFirstClaimedGroup
    )
  }

  def putBackDeviceToUnclaimedGroup(device: DeviceFE, groups: GroupList): DeviceFE = {
    device.addGroup(groups.unclaimedGroup.toGroupFE)
  }

  def removeFromGroup(device: DeviceFE, groups: GroupList): DeviceFE = {
    device.removeGroup(groups.userOwnDevicesGroup.toGroupFE)
      .removeGroup(groups.userFirstClaimedGroup.toGroupFE)
  }

  def removeClaimedAttributes(device: DeviceFE): DeviceFE = {
    device.removeFromAttributes(List(Elements.FIRST_CLAIMED_TIMESTAMP))
      .removeFromAttributes(List(Elements.CLAIMING_TAGS_NAME))
  }

  def removeConfigGroupAttributes(device: DeviceFE, groups: GroupList): DeviceFE = {
    val apiConfigGroupAttributes = groups.apiConfigGroup.getAttributesScala.keys.toList
    val deviceConfigGroupAttributes = groups.deviceConfigGroup.getAttributesScala.keys.toList
    device.removeFromAttributes(apiConfigGroupAttributes)
      .removeFromAttributes(deviceConfigGroupAttributes)
  }

  private def getUnclaimedDeviceGroup(deviceGroups: List[GroupRepresentation]) = deviceGroups.find(gr => gr.getName == Elements.UNCLAIMED_DEVICES_GROUP_NAME) match {
    case None => throw GroupNotFound(s"Device does not belong to the unclaimed group, device: ${device.toDeviceDumb}")
    case Some(value) => value
  }

  private def getApiGroup = try {
    GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
  } catch {
    case e: Throwable =>
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to find general devices apiGroup, device: ${device.toDeviceDumb}"
      logger.error(errorMessage, e)
      throw new InternalApiException(errorMessage)
  }

  private def getDeviceProviderName(deviceGroups: List[GroupRepresentation]): String = {
    val providerName = device.resource.getProviderName(Some(deviceGroups))
    if (providerName == "") {
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to find device provider, device: ${device.toDeviceDumb}"
      logger.error(errorMessage)
      throw new InternalApiException(errorMessage)
    } else {
      providerName
    }
  }

  private def getClaimedProviderGroup(deviceProviderName: String) = try {
    GroupFactory.getOrCreateGroup(Util.getProviderClaimedDevicesName(deviceProviderName))
  } catch {
    case e: Throwable =>
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to find device provider group, device: ${device.toDeviceDumb}"
      logger.error(errorMessage, e)
      throw new InternalApiException(errorMessage)
  }

  private def getNewDevicePassword: String = try {
    user.getPasswordForDevice()
  } catch {
    case e: Throwable =>
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to generate devices's new password, device: ${device.toDeviceDumb}"
      logger.error(errorMessage, e)
      throw new InternalApiException(errorMessage)
  }

  private def getNewApiAttributes(apiGroup: GroupResourceRepresentation, newPassword: String): GroupAttributes = try {
    GroupAttributes(apiGroup.representation.getAttributes.asScala.toMap).setValue("password", newPassword)
  } catch {
    case e: Throwable =>
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to replace password value in api group: apiGroup = ${apiGroup.representation.getAttributes.asScala.toMap}, device: ${device.toDeviceDumb}"
      logger.error(errorMessage, e)
      throw new InternalApiException(errorMessage)
  }

  private def getDeviceFE(deviceGroups: List[GroupRepresentation]): DeviceFE = try {
    device.toDeviceFE(Some(deviceGroups))
  } catch {
    case e: Throwable =>
      val errorMessage = s"CLAIMING - getWorkingCopy: unable to cast device to deviceFE, device: ${device.toDeviceDumb}"
      logger.error(errorMessage, e)
      throw new InternalApiException(errorMessage)
  }

}

case class GroupList(unclaimedGroup: GroupResourceRepresentation, apiConfigGroup: GroupResourceRepresentation, deviceConfigGroup: GroupResourceRepresentation, userOwnDevicesGroup: GroupResourceRepresentation, userFirstClaimedGroup: GroupResourceRepresentation)

case class DeviceCopy(groups: List[GroupRepresentation], oldDescription: String, oldOwner: MemberResourceRepresentation)
