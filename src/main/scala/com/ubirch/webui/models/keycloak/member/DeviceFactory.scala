package com.ubirch.webui.models.keycloak.member

import java.util
import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, MemberResourceRepresentation, QuickActions, Util }
import com.ubirch.webui.models.keycloak.AddDevice
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JString
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import scala.collection.JavaConverters._

object DeviceFactory extends LazyLogging {

  val memberType: MemberType.Value = MemberType.Device

  def getBySecondaryIndex(index: String, namingConvention: String, briefRepresentation: Boolean = false)(implicit realmName: String): UserRepresentation =
    MemberFactory.getByFirstName(index, namingConvention, briefRepresentation)

  /**
    * Given the correct parameters, will return the device whose username is the given hwDeviceId
    * @param hwDeviceId the UUID hwDeviceId that correspond to the keycloak username of the device
    * @param realmName
    * @return
    */
  def getByHwDeviceIdQuick(hwDeviceId: String)(implicit realmName: String): Either[String, UserRepresentation] = {
    if (Util.isStringUuid(hwDeviceId)) {
      Right(QuickActions.quickSearchUserNameOnlyOne(hwDeviceId))
    } else {
      Left(hwDeviceId)
    }
  }

  def getByHwDeviceId(hwDeviceId: String)(implicit realmName: String): Either[String, MemberResourceRepresentation] = {
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

    val apiConfigGroupAttr = GroupAttributes(apiConfigGroup.get().representation.getAttributes.asScala.toMap)

    val newlyCreatedDevice = createInitialDevice(device, apiConfigGroupAttr, deviceConfigGroup.get())

    val allGroupIds = device.listGroups :+ apiConfigGroup.get().representation.getId :+ deviceConfigGroup.get().representation.getId :+ unclaimedDevicesGroup.get().representation.getId :+ providerGroup.get().representation.getId
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }
    val res = newlyCreatedDevice.getUpdatedResource
    logger.debug(s"~~~~Created device ${device.hwDeviceId} with actual hwDeviceId ${res.toRepresentation.getUsername}")
    res
  }

  def createDevice(device: AddDevice, owner: MemberResourceRepresentation)(implicit realmName: String): UserResource = {
    Util.stopIfHwdeviceidIsNotUUID(device.hwDeviceId)
    Util.stopIfMemberAlreadyExist(device.hwDeviceId)

    val userOwnDeviceGroup = owner.getOwnDeviceGroup()
    val apiConfigGroup = GroupFactory.getByNameQuick(Util.getApiConfigGroupName(realmName)).toResourceRepresentation
    val deviceConfigGroup = GroupFactory.getByNameQuick(Util.getDeviceConfigGroupName(device.deviceType)).toResourceRepresentation

    val password = owner.getPasswordForDevice()

    val gAttr = GroupAttributes(apiConfigGroup.representation.getAttributes.asScala.toMap).setValue("password", password)

    val newlyCreatedDevice: UserResource = createInitialDevice(device, gAttr, deviceConfigGroup)

    val allGroupIds = device.listGroups :+ apiConfigGroup.representation.getId :+ deviceConfigGroup.representation.getId :+ userOwnDeviceGroup.toRepresentation.getId
    allGroupIds foreach { groupId =>
      newlyCreatedDevice.joinGroup(groupId)
    }

    newlyCreatedDevice.getUpdatedResource
  }

  private def createInitialDevice(device: AddDevice, apiConfigGroupAttributesUpdatedWithNewPassword: GroupAttributes, deviceConfigGroupAttributes: GroupResourceRepresentation)(implicit realmName: String): UserResource = {
    val deviceRepresentation = new UserRepresentation
    deviceRepresentation.setEnabled(true)
    deviceRepresentation.setUsername(device.hwDeviceId)

    if (!device.description.equals("")) {
      deviceRepresentation.setLastName(device.description)
    } else deviceRepresentation.setLastName(device.hwDeviceId)

    deviceRepresentation.setFirstName(device.secondaryIndex)

    setCredential(deviceRepresentation, apiConfigGroupAttributesUpdatedWithNewPassword.getValue("password"))

    val allAttributes: Map[String, util.List[String]] = apiConfigGroupAttributesUpdatedWithNewPassword.attributes ++
      deviceConfigGroupAttributes.representation.getAttributes.asScala.toMap ++
      device.attributes.mapValues(_.asJava)

    deviceRepresentation.setAttributes(allAttributes.asJava)

    val newDevice = createDeviceInKc(deviceRepresentation)
    newDevice.addRoles(List(Util.getRole(Elements.DEVICE).toRepresentation))
    newDevice.getUpdatedResource
  }

  private def setCredential(deviceRepresentation: UserRepresentation, devicePassword: String): Unit = {

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
    QuickActions.quickSearchId(deviceKcId)
  }

}

case class GroupAttributes(attributes: Map[String, util.List[String]]) {
  implicit val formats: DefaultFormats.type = DefaultFormats
  def getValue(key: String): String = {
    val json = parse(attributes.head._2.asScala.head)
    (json \ key).extract[String]
  }
  def asScala: Map[String, List[String]] = Util.attributesToMap(attributes.asJava)

  def setValue(key: String, value: String) = {
    import org.json4s.jackson.JsonMethods._
    import scala.collection.JavaConverters._
    val json = parse(attributes.head._2.asScala.head)
    val newValue = json.replace(key :: Nil, JString(value))
    val newList = List(compact(render(newValue)))
    copy(Map(attributes.head._1 -> newList.asJava))
  }
}
