package com.ubirch.webui.core.structure.member

import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.structure.{ AddDevice, Elements }
import com.ubirch.webui.core.structure.group.{ Group, GroupFactory }
import com.ubirch.webui.core.structure.util.Util
import org.keycloak.models.AbstractKeycloakTransaction

class ClaimTransaction(device: Device, prefix: String, tags: String, user: User)(implicit realmName: String) extends AbstractKeycloakTransaction with LazyLogging {

  override def commitImpl(): Unit = {

    val unclaimedGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getApiConfigGroupName(realmName))
    }, 5, TimeUnit.MINUTES)

    lazy val deviceConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getDeviceConfigGroupName(device.getDeviceType))
    }, 5, TimeUnit.MINUTES)

    val apiConfigGroupAttributes = apiConfigGroup.get().getAttributes.attributes.keys.toList
    val deviceConfigGroupAttributes = deviceConfigGroup.get().getAttributes.attributes.keys.toList

    device.leaveGroup(unclaimedGroup)

    val addDeviceStruct = device.toAddDevice

    val addDeviceStructUpdated: AddDevice = addDeviceStruct
      .addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString)))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> List(tags)))
      .removeFromAttributes(apiConfigGroupAttributes)
      .removeFromAttributes(deviceConfigGroupAttributes)
      .addGroup(user.getOrCreateFirstClaimedGroup.name)
      .removeGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
      .addPrefixToDescription(prefix)

    device.updateDevice(
      newOwners = List(user),
      deviceUpdateStruct = addDeviceStructUpdated,
      deviceConfig = addDeviceStruct.attributes,
      apiConfig = addDeviceStruct.attributes
    )
  }

  override def rollbackImpl(): Unit = {
    logger.info("Rolling back data change to the claiming of device")
    var addDeviceStruct: AddDevice = device.toAddDevice
    val groups = getGroups(device)
    addDeviceStruct = putBackDeviceToUnclaimedGroup(addDeviceStruct, groups)
    addDeviceStruct = removeFromGroup(addDeviceStruct, groups)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    device.updateDevice(
      newOwners = List(),
      deviceUpdateStruct = addDeviceStruct,
      deviceConfig = addDeviceStruct.attributes,
      apiConfig = addDeviceStruct.attributes
    )
  }

  def getGroups(device: Device): GroupList = {
    GroupList(GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME), GroupFactory.getByName(Util.getApiConfigGroupName(realmName)), GroupFactory.getByName(Util.getDeviceConfigGroupName(device.getDeviceType)), user.getOwnDeviceGroup, user.getOrCreateFirstClaimedGroup)
  }

  def putBackDeviceToUnclaimedGroup(device: AddDevice, groups: GroupList): AddDevice = {
    device.addGroup(groups.unclaimedGroup.name)
  }

  def removeFromGroup(device: AddDevice, groups: GroupList): AddDevice = {
    device.removeGroup(groups.userOwnDevicesGroup.name)
      .removeGroup(groups.userFirstClaimedGroup.name)
  }

  def removeClaimedAttributes(device: AddDevice): AddDevice = {
    device.removeFromAttributes(List(Elements.FIRST_CLAIMED_TIMESTAMP))
      .removeFromAttributes(List(Elements.CLAIMING_TAGS_NAME))
  }

  def removeConfigGroupAttributes(device: AddDevice, groups: GroupList): AddDevice = {
    val apiConfigGroupAttributes = groups.apiConfigGroup.getAttributes.attributes.keys.toList
    val deviceConfigGroupAttributes = groups.deviceConfigGroup.getAttributes.attributes.keys.toList
    device.removeFromAttributes(apiConfigGroupAttributes)
      .removeFromAttributes(deviceConfigGroupAttributes)
  }

}

case class GroupList(unclaimedGroup: Group, apiConfigGroup: Group, deviceConfigGroup: Group, userOwnDevicesGroup: Group, userFirstClaimedGroup: Group)
