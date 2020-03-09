package com.ubirch.webui.core.structure.member

import java.util
import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.group.{ Group, GroupAttributes, GroupFactory }
import com.ubirch.webui.core.structure.util.Util
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import scala.collection.JavaConverters._

object DeviceFactory extends LazyLogging {

  val memberType: MemberType.Value = MemberType.Device

  def getBySecondaryIndex(index: String, namingConvention: String)(implicit realmName: String): Device =
    MemberFactory.getByFirstNameStrict(index, memberType, namingConvention).asInstanceOf[Device]

  def getByHwDeviceId(hwDeviceId: String)(implicit realmName: String): Device =
    MemberFactory.getByUsername(hwDeviceId, memberType).asInstanceOf[Device]

  def getByDescription(description: String)(implicit realmName: String): Device =
    MemberFactory.getByAName(description, memberType).asInstanceOf[Device]

  def searchMultipleDevices(searchThing: String)(implicit realmName: String): List[Device] =
    MemberFactory
      .getMultiple(searchThing, memberType, 100000)
      .asInstanceOf[List[Device]]

  protected[structure] def createDeviceAdmin(device: AddDevice, provider: String)(implicit realmName: String): Device = {
    logger.debug(s"~~ Creating device admin for device with hwDeviceId: ${device.hwDeviceId}")
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)
    Util.stopIfMemberAlreadyExistSecondaryIndex(device.secondaryIndex)

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getApiConfigGroupName(realmName))
    }, 5, TimeUnit.MINUTES)

    lazy val deviceConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getByName(Util.getDeviceConfigGroupName(device.deviceType))
    }, 5, TimeUnit.MINUTES)

    lazy val unclaimedDevicesGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
    }, 5, TimeUnit.MINUTES)

    lazy val providerGroup = Suppliers.memoizeWithExpiration(new Supplier[Group] {
      override def get(): Group = GroupFactory.getOrCreateGroup(Util.getProviderGroupName(provider))
    }, 5, TimeUnit.MINUTES)

    val newlyCreatedDevice: Device = createInitialDevice(device, apiConfigGroup.get(), deviceConfigGroup.get())

    val allGroupIds = device.listGroups :+ apiConfigGroup.get().id :+ deviceConfigGroup.get().id :+ unclaimedDevicesGroup.get().id :+ providerGroup.get().id
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }
    val res = newlyCreatedDevice.getUpdatedDevice
    logger.debug(s"~~~~Created device ${device.hwDeviceId} with actual hwDeviceId ${res.getUsername}")
    res
  }

  protected[structure] def createDevice(device: AddDevice, owner: User)(implicit realmName: String): Device = {
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)

    val userOwnDeviceGroup = owner.getOwnDeviceGroup
    val apiConfigGroup = GroupFactory.getByName(Util.getApiConfigGroupName(realmName))
    val deviceConfigGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(device.deviceType))

    val newlyCreatedDevice: Device = createInitialDevice(device, apiConfigGroup, deviceConfigGroup)

    val allGroupIds = device.listGroups :+ apiConfigGroup.id :+ deviceConfigGroup.id :+ userOwnDeviceGroup.id
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }

    newlyCreatedDevice.getUpdatedDevice
  }

  private def createInitialDevice(device: AddDevice, apiConfigGroupAttributes: Group, deviceConfigGroupAttributes: Group)(implicit realmName: String): Device = {
    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setEnabled(true)
    deviceRepresentation.setUsername(device.hwDeviceId)

    if (!device.description.equals("")) {
      deviceRepresentation.setLastName(device.description)
    } else deviceRepresentation.setLastName(device.hwDeviceId)

    deviceRepresentation.setFirstName(device.secondaryIndex)
    setCredential(deviceRepresentation, apiConfigGroupAttributes.getAttributes)

    val allAttributes: Map[String, util.List[String]] = apiConfigGroupAttributes.getAttributes.attributes ++
      deviceConfigGroupAttributes.getAttributes.attributes ++
      device.attributes.mapValues(_.asJava)

    deviceRepresentation.setAttributes(allAttributes.asJava)

    val newDevice = createDeviceInKc(deviceRepresentation)
    newDevice.addRole(Util.getRole(Elements.DEVICE).toRepresentation)
    newDevice.getUpdatedDevice
  }

  private def setCredential(deviceRepresentation: UserRepresentation, apiConfigGroupAttributes: GroupAttributes): Unit = {

    val devicePassword = apiConfigGroupAttributes.getValue("password")

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(devicePassword)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(Util.singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))
  }

  private def createDeviceInKc(deviceRepresentation: UserRepresentation)(implicit realmName: String) = {
    val realm = Util.getRealm
    logger.debug(s"~~~|| sending actual keycloak request to create device with hwDeviceId ${deviceRepresentation.getUsername}")
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    logger.debug(s"~~~|| actual creation on keycloak done, for hwDeviceId ${deviceRepresentation.getUsername} the id is $deviceKcId. Now trying to query it")
    DeviceFactory.getByKeyCloakId(deviceKcId)
  }

  def getByKeyCloakId(kcId: String)(implicit realmName: String): Device =
    MemberFactory.getById(kcId, memberType).asInstanceOf[Device]

}
