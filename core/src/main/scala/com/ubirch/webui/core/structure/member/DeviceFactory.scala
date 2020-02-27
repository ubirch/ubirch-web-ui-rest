package com.ubirch.webui.core.structure.member

import java.util

import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.group.{ Group, GroupAttributes, GroupFactory }
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import scala.collection.JavaConverters._

object DeviceFactory {

  val memberType: MemberType.Value = MemberType.Device

  def getBySecondaryIndex(index: String)(implicit realmName: String): Device =
    MemberFactory.getByAName(index, memberType).asInstanceOf[Device]

  def getByHwDeviceId(hwDeviceId: String)(implicit realmName: String): Device =
    MemberFactory.getByUsername(hwDeviceId, memberType).asInstanceOf[Device]

  def getByDescription(description: String)(implicit realmName: String): Device =
    MemberFactory.getByAName(description, memberType).asInstanceOf[Device]

  def searchMultipleDevices(searchThing: String)(implicit realmName: String): List[Device] =
    MemberFactory
      .getMultiple(searchThing, memberType, 100000)
      .asInstanceOf[List[Device]]

  protected[structure] def createDevice(device: AddDevice, owner: User)(implicit realmName: String): Device = {
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)

    val userOwnDeviceGroup = owner.getOwnDeviceGroup
    val apiConfigGroup = GroupFactory.getByName(Util.getApiConfigGroupName(realmName))
    val deviceConfigGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(device.deviceType))

    val newlyCreatedDevice: Device =
      createInitialDevice(device, apiConfigGroup, deviceConfigGroup)

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

    val devicePassword =
      apiConfigGroupAttributes.getValue("password")

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(devicePassword)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(
      Util
        .singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential)
    )
  }

  private def createDeviceInKc(deviceRepresentation: UserRepresentation)(implicit realmName: String) = {
    val realm = Util.getRealm
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    DeviceFactory.getByKeyCloakId(deviceKcId)
  }

  def getByKeyCloakId(kcId: String)(implicit realmName: String): Device =
    MemberFactory.getById(kcId, memberType).asInstanceOf[Device]

}
