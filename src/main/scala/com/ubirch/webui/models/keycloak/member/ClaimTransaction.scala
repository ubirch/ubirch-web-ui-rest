package com.ubirch.webui.models.keycloak.member

import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.DeviceFE
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, MemberResourceRepresentation, Util }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.Exceptions.InternalApiException
import org.keycloak.models.AbstractKeycloakTransaction

class ClaimTransaction(device: MemberResourceRepresentation, prefix: String, tags: List[String], user: MemberResourceRepresentation, newDescription: String)(implicit realmName: String) extends AbstractKeycloakTransaction with LazyLogging {

  override def commitImpl(): Unit = {

    val unclaimedGroup = GroupFactory.getByNameQuick(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    val deviceGroup = Some(device.resource.getAllGroups())

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
    }, 5, TimeUnit.MINUTES)

    lazy val deviceConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getByNameQuick(Util.getDeviceConfigGroupName(device.resource.getDeviceType(deviceGroup))).toResourceRepresentation
    }, 5, TimeUnit.MINUTES)

    val apiConfigGroupAttributes = apiConfigGroup.get().getAttributesScala.keys.toList
    val deviceConfigGroupAttributes = deviceConfigGroup.get().getAttributesScala.keys.toList

    // using the getOrCreate even though we only call the name after to make sure that the group is created
    val claimedGroupProvider = GroupFactory.getOrCreateGroup(Util.getProviderClaimedDevicesName(device.resource.getProviderName(deviceGroup)))

    val unclaimedDeviceGroup = GroupFactory.getByNameQuick(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

    device.leaveGroup(unclaimedGroup)

    val addDeviceStruct = device.toDeviceFE

    val addDeviceStructUpdated: DeviceFE = addDeviceStruct
      .addToAttributes(Map(Elements.FIRST_CLAIMED_TIMESTAMP -> List(Util.getCurrentTimeIsoString)))
      .addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> tags))
      .addGroup(user.getOrCreateFirstClaimedGroup.representation.toGroupFE)
      .addGroup(claimedGroupProvider.representation.toGroupFE)
      .removeGroup(unclaimedDeviceGroup.toGroupFE)
      .copy(description = newDescription)
      .addPrefixToDescription(prefix)

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

}

case class GroupList(unclaimedGroup: GroupResourceRepresentation, apiConfigGroup: GroupResourceRepresentation, deviceConfigGroup: GroupResourceRepresentation, userOwnDevicesGroup: GroupResourceRepresentation, userFirstClaimedGroup: GroupResourceRepresentation)
