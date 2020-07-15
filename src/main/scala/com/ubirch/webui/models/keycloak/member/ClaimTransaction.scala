package com.ubirch.webui.models.keycloak.member

import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.keycloak.group.{ Group, GroupFactory }
import com.ubirch.webui.models.keycloak.DeviceFE
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.Elements
import org.keycloak.models.AbstractKeycloakTransaction

class ClaimTransaction(device: Device, prefix: String, tags: List[String], user: User)(implicit realmName: String) extends AbstractKeycloakTransaction with LazyLogging {

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

    // using the getOrCreate even though we only call the name after to make sure that the group is created
    val claimedGroupProvider = GroupFactory.getOrCreateGroup(Util.getProviderClaimedDevicesName(device.getProviderName))

    val unclaimedDeviceGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    device.leaveGroup(unclaimedGroup)

    val addDeviceStruct = device.toDeviceFE
    println(addDeviceStruct.toString)

    val addDeviceStructUpdated: DeviceFE = addDeviceStruct
      .addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString)))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> List(tags.mkString(", "))))
      .addGroup(user.getOrCreateFirstClaimedGroup.toGroupFE)
      .addGroup(claimedGroupProvider.toGroupFE)
      .removeGroup(unclaimedDeviceGroup.toGroupFE)
      .addPrefixToDescription(prefix)
    println(addDeviceStructUpdated.toString)
    device.updateDevice(
      addDeviceStructUpdated.copy(owner = List(user.toSimpleUser))
    )
  }

  override def rollbackImpl(): Unit = {
    logger.info("Rolling back data change to the claiming of device")
    var addDeviceStruct = device.toDeviceFE
    val groups = getGroups(device)
    addDeviceStruct = putBackDeviceToUnclaimedGroup(addDeviceStruct, groups)
    addDeviceStruct = removeFromGroup(addDeviceStruct, groups)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    addDeviceStruct = removeClaimedAttributes(addDeviceStruct)
    device.updateDevice(
      addDeviceStruct.copy(owner = List(user.toSimpleUser))
    )
  }

  def getGroups(device: Device): GroupList = {
    GroupList(GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME), GroupFactory.getByName(Util.getApiConfigGroupName(realmName)), GroupFactory.getByName(Util.getDeviceConfigGroupName(device.getDeviceType)), user.getOwnDeviceGroup, user.getOrCreateFirstClaimedGroup)
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
    val apiConfigGroupAttributes = groups.apiConfigGroup.getAttributes.attributes.keys.toList
    val deviceConfigGroupAttributes = groups.deviceConfigGroup.getAttributes.attributes.keys.toList
    device.removeFromAttributes(apiConfigGroupAttributes)
      .removeFromAttributes(deviceConfigGroupAttributes)
  }

}

case class GroupList(unclaimedGroup: Group, apiConfigGroup: Group, deviceConfigGroup: Group, userOwnDevicesGroup: Group, userFirstClaimedGroup: Group)
