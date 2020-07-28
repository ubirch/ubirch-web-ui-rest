package com.ubirch.webui.models.keycloak.member

import java.util
import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.group.{ GroupAttributes, GroupFactory }
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, MemberResourceRepresentation, QuickActions, Util }
import com.ubirch.webui.models.keycloak.AddDevice
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import scala.collection.JavaConverters._

object DeviceFactory extends LazyLogging {

  val memberType: MemberType.Value = MemberType.Device

  def getBySecondaryIndex(index: String, namingConvention: String)(implicit realmName: String): UserRepresentation =
    MemberFactory.getByFirstName(index, namingConvention)

  /**
    * Given the correct parameters, will return the device whose username is the given hwDeviceId
    * @param hwDeviceId the UUID hwDeviceId that correspond to the keycloak username of the device
    * @param realmName
    * @return
    */
  def getByHwDeviceId(hwDeviceId: String)(implicit realmName: String): Either[String, UserRepresentation] = {
    if (Util.isStringUuid(hwDeviceId)) {
      Right(QuickActions.quickSearchUserNameOnlyOne(hwDeviceId))
    } else {
      Left(hwDeviceId)
    }
  }

  def getByHwDeviceIdQuick(hwDeviceId: String)(implicit realmName: String): Either[String, MemberResourceRepresentation] = {
    if (Util.isStringUuid(hwDeviceId)) {
      val representation = QuickActions.quickSearchUserNameOnlyOne(hwDeviceId)
      val resource = Util.getRealm.users().get(representation.getId)
      Right(MemberResourceRepresentation(resource, representation))
    } else {
      Left(hwDeviceId)
    }
  }

  def getByDescription(description: String)(implicit realmName: String): UserRepresentation =
    QuickActions.quickSearchName(description)

  def searchMultipleDevices(searchThing: String)(implicit realmName: String): List[UserRepresentation] =
    MemberFactory.getMultiple(searchThing, 100000)

  protected[keycloak] def createDeviceAdmin(device: AddDevice, provider: String)(implicit realmName: String): UserResource = {
    logger.debug(s"~~ Creating device admin for device with hwDeviceId: ${device.hwDeviceId}")
    Util.stopIfHwdeviceidIsNotUUID(device.hwDeviceId)
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)
    Util.stopIfMemberAlreadyExistSecondaryIndex(device.secondaryIndex)

    lazy val apiConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
    }, 5, TimeUnit.MINUTES)

    lazy val deviceConfigGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getByNameQuick(Util.getDeviceConfigGroupName(device.deviceType)).toResourceRepresentation
    }, 5, TimeUnit.MINUTES)

    lazy val unclaimedDevicesGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
    }, 5, TimeUnit.MINUTES)

    lazy val providerGroup = Suppliers.memoizeWithExpiration(new Supplier[GroupResourceRepresentation] {
      override def get(): GroupResourceRepresentation = GroupFactory.getOrCreateGroup(Util.getProviderGroupName(provider))
    }, 5, TimeUnit.MINUTES)

    val newlyCreatedDevice = createInitialDevice(device, apiConfigGroup.get(), deviceConfigGroup.get())

    val allGroupIds = device.listGroups :+ apiConfigGroup.get().representation.getId :+ deviceConfigGroup.get().representation.getId :+ unclaimedDevicesGroup.get().representation.getId :+ providerGroup.get().representation.getId
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }
    val res = newlyCreatedDevice.getUpdatedResource
    logger.debug(s"~~~~Created device ${device.hwDeviceId} with actual hwDeviceId ${res.toRepresentation.getUsername}")
    res
  }

  def createDevice(device: AddDevice, owner: UserResource)(implicit realmName: String): UserResource = {
    Util.stopIfHwdeviceidIsNotUUID(device.hwDeviceId)
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)

    val userOwnDeviceGroup = owner.getOwnDeviceGroup()
    val apiConfigGroup = GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
    val deviceConfigGroup = GroupFactory.getByNameQuick(Util.getDeviceConfigGroupName(device.deviceType)).toResourceRepresentation

    val newlyCreatedDevice: UserResource = createInitialDevice(device, apiConfigGroup, deviceConfigGroup)

    val allGroupIds = device.listGroups :+ apiConfigGroup.representation.getId :+ deviceConfigGroup.representation.getId :+ userOwnDeviceGroup.getId
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }

    newlyCreatedDevice.getUpdatedResource
  }

  private def createInitialDevice(device: AddDevice, apiConfigGroupAttributes: GroupResourceRepresentation, deviceConfigGroupAttributes: GroupResourceRepresentation)(implicit realmName: String): UserResource = {
    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setEnabled(true)
    deviceRepresentation.setUsername(device.hwDeviceId)

    if (!device.description.equals("")) {
      deviceRepresentation.setLastName(device.description)
    } else deviceRepresentation.setLastName(device.hwDeviceId)

    deviceRepresentation.setFirstName(device.secondaryIndex)
    setCredential(deviceRepresentation, GroupAttributes(apiConfigGroupAttributes.representation.getAttributes.asScala.toMap))

    val allAttributes: Map[String, util.List[String]] = apiConfigGroupAttributes.representation.getAttributes.asScala.toMap ++
      deviceConfigGroupAttributes.representation.getAttributes.asScala.toMap ++
      device.attributes.mapValues(_.asJava)

    deviceRepresentation.setAttributes(allAttributes.asJava)

    val newDevice = createDeviceInKc(deviceRepresentation)
    newDevice.addRoles(List(Util.getRole(Elements.DEVICE).toRepresentation))
    newDevice.getUpdatedResource
  }

  private def setCredential(deviceRepresentation: UserRepresentation, apiConfigGroupAttributes: GroupAttributes): Unit = {

    val devicePassword = apiConfigGroupAttributes.getValue("password")

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue(devicePassword)
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(Util.singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))
  }

  private def createDeviceInKc(deviceRepresentation: UserRepresentation)(implicit realmName: String): UserResource = {
    val realm = Util.getRealm
    logger.debug(s"~~~|| sending actual keycloak request to create device with hwDeviceId ${deviceRepresentation.getUsername}")
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    logger.debug(s"~~~|| actual creation on keycloak done, for hwDeviceId ${deviceRepresentation.getUsername} the id is $deviceKcId. Now trying to query it")
    DeviceFactory.getByKeyCloakId(deviceKcId)
  }

  def getByKeyCloakId(kcId: String)(implicit realmName: String): UserResource =
    QuickActions.quickSearchId(kcId)

}
