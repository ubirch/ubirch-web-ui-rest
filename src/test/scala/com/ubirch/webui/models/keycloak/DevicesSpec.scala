package com.ubirch.webui.models.keycloak

import java.util.Base64

import com.ubirch.webui._
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member.{ DeviceCreationSuccess, DeviceFactory }
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.TestRefUtil.{ giveMeRandomString, giveMeRandomUUID }
import com.ubirch.webui.batch.SIM
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ BadOwner, InternalApiException, NotAuthorized }
import javax.ws.rs.NotFoundException
import org.json4s.{ DefaultFormats, NoTypeHints }
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }
import org.json4s.jackson.JsonMethods.parse
import org.json4s.native.Serialization

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext }

class DevicesSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  val defaultUser: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
  val defaultDevice: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", true)
  val defaultUserDevice = UserDevices(defaultUser, maybeDevicesShould = Option(List(defaultDevice)))
  val defaultUsers = Option(UsersDevices(List(defaultUserDevice)))
  val defaultApiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
  val defaultDeviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
  val defaultConfGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup)))

  def defaultInitKeycloakBuilder = InitKeycloakBuilder(users = defaultUsers, defaultGroups = defaultConfGroups)

  val devicePwdGroup = "123e4567-e89b-12d3-a456-426614174000"
  val groupPasswordName: String = Elements.DEFAULT_PASSWORD_GROUP_PREFIX + "test"
  val userNoDevice = UserDevices(defaultUser, maybeDevicesShould = None, Some(List(groupPasswordName)))
  val usersWithoutDevices = Option(UsersDevices(List(userNoDevice)))
  val groupPasswordAttributes = Map(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE -> List(devicePwdGroup).asJava).asJava
  val groupPasswordWithAttributes = GroupWithAttribute(groupPasswordName, groupPasswordAttributes)
  val confGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup, groupPasswordWithAttributes)))

  val initKeycloakBuilderNoDevicePassword = InitKeycloakBuilder(users = usersWithoutDevices, defaultGroups = confGroups)

  val userDevicePassword = UserDevices(defaultUser, maybeDevicesShould = Option(List(defaultDevice)), Some(List(groupPasswordName)))
  val usersWithDevicePassword = Option(UsersDevices(List(userDevicePassword)))
  val initKeycloakBuilderPassword = InitKeycloakBuilder(users = usersWithDevicePassword, defaultGroups = confGroups)
  /*
  A default keycloak env: one user, that has no device
   */
  def initKeycloakBuilderNoDevice = InitKeycloakBuilder(
    users = Option(UsersDevices(List(UserDevices(defaultUser, maybeDevicesShould = None)))),
    defaultGroups = defaultConfGroups
  )

  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
  val providerName = "provider"

  feature("create device") {
    scenario("create single device") {

      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevicePassword)

      val user = keycloakBuilderResponse.usersResponse.head.userResult.is
      // vals
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, _, deviceConfName) = TestRefUtil.createGroupsName(user.getUsername, realmName, deviceType)
      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"
      val attr = Map(
        "attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF),
        "test" -> List("coucou", "test", "salut")
      )

      // create groups
      val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)

      val listGroupsToJoinId = List(randomGroupKc.id)

      val deviceConfigGroup = GroupFactory.getByName(deviceConfName)

      val r = DeviceFactory.createDevice(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, attr), user).toResourceRepresentation //user.createNewDevice(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, attr))

      // get user password
      val devicePwd = Some(user.getPasswordForDevice())

      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAdded(
        Elements.DEVICE,
        hwDeviceId,
        keycloakBuilderResponse.getApiConfigGroup.get.is,
        deviceConfigGroup,
        userGroupName,
        listGroupsToJoinId,
        deviceDescription,
        Some(attr),
        devicePwd
      )
    }

    scenario("add a device whose hwDeviceId is not a valid UUID -> FAIL") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      val (validHwDeviceId, deviceType, deviceDescription1) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")

      val badHwDeviceId = giveMeRandomString(validHwDeviceId.length)

      // create additional groups
      val randomGroupKc = TestRefUtil.createSimpleGroup("random_group")
      val listGroupsToJoinId = List(randomGroupKc.id)

      val device = AddDevice(badHwDeviceId, deviceDescription1, deviceType, listGroupsToJoinId)
      val user = builderResponse.usersResponse.head.userResult.is

      assertThrows[InternalApiException](
        DeviceFactory.createDevice(device, user)
      )
    }

    scenario("add a device that already exist -> FAIL") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      val (hwDeviceId1, deviceType, deviceDescription1) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")

      // create additional groups
      val randomGroupKc = TestRefUtil.createSimpleGroup("random_group")
      val listGroupsToJoinId = List(randomGroupKc.id)

      val device = AddDevice(hwDeviceId1, deviceDescription1, deviceType, listGroupsToJoinId)
      val user = builderResponse.usersResponse.head.userResult.is
      DeviceFactory.createDevice(device, user)

      assertThrows[InternalApiException](
        DeviceFactory.createDevice(device, user)
      )
    }

    scenario("add a device that already exist with batch -> FAIL") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")

      // create additional groups
      val randomGroupKc = TestRefUtil.createSimpleGroup("random_group")
      val listGroupsToJoinId = List(randomGroupKc.id)

      val device = AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
      val user = builderResponse.usersResponse.head.userResult.is
      DeviceFactory.createDevice(device, user)

      val res = Await.result(user.createMultipleDevices(List(device)), 1.minutes)

      res.head.toString shouldBe s"""DeviceCreationFail($hwDeviceId,member with username: $hwDeviceId already exists,1)"""
      logger.info(res.map { r => r.toString }.mkString(", "))

    }

    scenario("bulk creation of correct device, size = 1") {

      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevicePassword)

      val user = keycloakBuilderResponse.usersResponse.head.userResult.is

      // vals

      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(user.getUsername, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles

      val deviceToAdd = AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
      val res = Await.result(user.createMultipleDevices(List(deviceToAdd)), 1.minute)
      res shouldBe List(DeviceCreationSuccess(hwDeviceId))

      // get user password
      val devicePwd = Some(user.getPasswordForDevice())

      val deviceConfigGroup = GroupFactory.getByName(deviceConfName)

      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAdded(
        deviceRoleName = Elements.DEVICE,
        hwDeviceId = hwDeviceId,
        keycloakBuilderResponse.getApiConfigGroup.get.is,
        deviceConfigGroup = deviceConfigGroup,
        userGroupName = userGroupName,
        listGroupsId = listGroupsToJoinId,
        description = deviceDescription,
        additionalAttributes = None,
        maybePassword = devicePwd
      )
    }

    scenario("bulk creation of correct devices, size > 1") {
      // initialize keycloak with one user that doesn't own any device

      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevicePassword)

      val user = keycloakBuilderResponse.usersResponse.head.userResult

      val (hwDeviceId1, deviceType, deviceDescription1) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")
      val (hwDeviceId2, _, deviceDescription2) = TestRefUtil.generateDeviceAttributes(description = "2a cool description")
      val (hwDeviceId3, _, deviceDescription3) = TestRefUtil.generateDeviceAttributes(description = "3a cool description")
      val (hwDeviceId4, _, deviceDescription4) = TestRefUtil.generateDeviceAttributes(description = "4a cool description")

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      // create additional groups
      val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles

      val ourList = List(
        AddDevice(hwDeviceId1, deviceDescription1, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId2, deviceDescription2, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId3, deviceDescription3, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId4, deviceDescription4, deviceType, listGroupsToJoinId)
      )
      val res = Await.result(user.is.createMultipleDevices(ourList), 1.minutes)

      val resShouldBe = ourList map { d => DeviceCreationSuccess(d.hwDeviceId) }

      res.sortBy(r => r.hwDeviceId) shouldBe resShouldBe.sortBy(r => r.hwDeviceId)

      // get user password
      Thread.sleep(1000)
      val devicePwd = Some(user.is.getPasswordForDevice())

      // verify
      ourList foreach { d =>
        TestRefUtil.verifyDeviceWasCorrectlyAdded(
          deviceRoleName = Elements.DEVICE,
          hwDeviceId = d.hwDeviceId,
          apiConfigGroup = keycloakBuilderResponse.getApiConfigGroup.get.is,
          deviceConfigGroup = keycloakBuilderResponse.getDeviceGroup().get.is,
          userGroupName = Util.getDeviceGroupNameFromUserName(user.should.username),
          listGroupsId = listGroupsToJoinId,
          description = d.description,
          additionalAttributes = None,
          maybePassword = devicePwd
        )
      }
    }
  }

  feature("devices added through the admin process tests") {
    scenario("add 1 device") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      // create admin groups
      GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
      // create random group
      val randomGroupName = "random_group"

      val randomGroup2Name = "random_group_2"
      val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      // create user
      val userResult = builderResponse.usersResponse.head.userResult
      val user = userResult.is

      // create device
      val listGroupsToJoinId = List(randomGroupKc.id)
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")
      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, secondaryIndex = "coucou"), providerName)

      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAddedAdmin(
        Elements.DEVICE,
        hwDeviceId,
        builderResponse.getApiConfigGroup.get.is,
        builderResponse.getDeviceGroup().get.is,
        Util.getDeviceGroupNameFromUserName(userResult.should.username),
        listGroupsToJoinId,
        deviceDescription,
        providerName,
        secondaryIndex = "coucou"
      )
    }

    scenario("n devices addition: using cache") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      val (hwDeviceId, deviceType, deviceDescription, sec1) = TestRefUtil.generateDeviceAttributesWithSecIndex(description = "a cool description")
      val (hwDeviceId2, _, deviceDescription2, sec2) = TestRefUtil.generateDeviceAttributesWithSecIndex(description = "2a cool description")
      val (hwDeviceId3, _, deviceDescription3, sec3) = TestRefUtil.generateDeviceAttributesWithSecIndex(description = "3a cool description")
      val (hwDeviceId4, _, deviceDescription4, sec4) = TestRefUtil.generateDeviceAttributesWithSecIndex(description = "4a cool description")

      GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      // create groups
      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"
      val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)

      // create user
      val userResult = builderResponse.usersResponse.head.userResult
      val user = userResult.is

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles

      val ourList = List(
        AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, secondaryIndex = sec1),
        AddDevice(hwDeviceId2, deviceDescription2, deviceType, listGroupsToJoinId, secondaryIndex = sec2),
        AddDevice(hwDeviceId3, deviceDescription3, deviceType, listGroupsToJoinId, secondaryIndex = sec3),
        AddDevice(hwDeviceId4, deviceDescription4, deviceType, listGroupsToJoinId, secondaryIndex = sec4)
      )

      ourList foreach { d =>
        {
          val t0 = System.currentTimeMillis()
          user.createNewDeviceAdmin(d, providerName)
          logger.info(s"took ${System.currentTimeMillis() - t0} to create device")
        }
      }

      ourList foreach { d =>
        TestRefUtil.verifyDeviceWasCorrectlyAddedAdmin(
          Elements.DEVICE,
          d.hwDeviceId,
          builderResponse.getApiConfigGroup.get.is,
          builderResponse.getDeviceGroup().get.is,
          Util.getDeviceGroupNameFromUserName(userResult.should.username),
          listGroupsToJoinId,
          d.description,
          providerName,
          secondaryIndex = d.secondaryIndex
        )
      }

    }

    scenario("add one device and claim it") {

      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      // create user
      val userResult = builderResponse.usersResponse.head.userResult
      val user = userResult.is

      val listGroupsToJoinId = Nil

      val imsi = "1111"
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, secondaryIndex = imsi), providerName)
      val claimingTags = List("ah que coucou", "test")
      val newDescription = "newDescription"
      user.claimDevice(imsi, "imsi", claimingTags, "imsi", newDescription)
      // verify
      TestRefUtil.verifyDeviceWasCorrectlyClaimed(
        hwDeviceId = hwDeviceId,
        apiConfigGroup = builderResponse.getApiConfigGroup.get.is,
        ownerUsername = userResult.should.username,
        deviceConfigGroup = builderResponse.getDeviceGroup().get.is,
        listGroupsId = Nil,
        description = "imsi" + newDescription,
        provider = providerName,
        secondaryIndex = imsi,
        claimingTags = claimingTags
      )

      val deviceClaimed = DeviceFactory.getBySecondaryIndex(imsi, "imsi").toResourceRepresentation
      deviceClaimed.resource.isClaimed() shouldBe true
      println(deviceClaimed.toDeviceFE().toString)
    }

    scenario("add one device and claim it group password") {

      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
      val defaultPwdGroup = GroupFactory.getOrCreateGroup(Elements.DEFAULT_PASSWORD_GROUP_PREFIX + "test")

      val groupRepresentation = defaultPwdGroup.representation
      groupRepresentation.setAttributes(Map(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE -> List(devicePwdGroup).asJava).asJava)

      defaultPwdGroup.resource.update(groupRepresentation)

      // create user
      val userResult = builderResponse.usersResponse.head.userResult
      val user = userResult.is

      user.joinGroupById(defaultPwdGroup.representation.getId)

      val listGroupsToJoinId = Nil

      val imsi = "1111"
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, secondaryIndex = imsi), providerName)
      val claimingTags = List("ah que coucou", "test")
      val newDescription = "newDescription"
      user.claimDevice(imsi, "imsi", claimingTags, "imsi", newDescription)
      // verify
      TestRefUtil.verifyDeviceWasCorrectlyClaimed(
        hwDeviceId = hwDeviceId,
        apiConfigGroup = builderResponse.getApiConfigGroup.get.is,
        ownerUsername = userResult.should.username,
        deviceConfigGroup = builderResponse.getDeviceGroup().get.is,
        listGroupsId = Nil,
        description = "imsi" + newDescription,
        provider = providerName,
        secondaryIndex = imsi,
        claimingTags = claimingTags
      )

      val deviceClaimed = DeviceFactory.getBySecondaryIndex(imsi, "imsi").toResourceRepresentation
      deviceClaimed.resource.isClaimed() shouldBe true
      println(deviceClaimed.toDeviceFE().toString)
      val apiAttributeHead = deviceClaimed.toDeviceFE().attributes(Elements.ATTRIBUTES_API_GROUP_NAME).toArray.head.toString
      implicit val formats: DefaultFormats.type = DefaultFormats
      val json = parse(apiAttributeHead)
      val pwd = (json \ "password").extract[String]
      Auth.auth(hwDeviceId, Base64.getEncoder.encodeToString(pwd.getBytes())) // check if password displayed and actual password are the same
    }

  }

  feature("delete / unclaim device") {
    scenario("delete existing device") {
      val device = TestRefUtil.createRandomDeviceFromEmptyKeycloak()
      device.representation.delete
      assertThrows[NotFoundException](
        realm.users().get(device.representation.getId).toRepresentation
      )
    }

    scenario("FAIL - delete existing device from bad user") {
      val device = TestRefUtil.createRandomDeviceFromEmptyKeycloak()
      val user = TestRefUtil.createSimpleUser()
      assertThrows[BadOwner](user.representation.deleteOwnDevice(device))
    }

    scenario("add one device and claim then unclaim it") {

      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderNoDevice)

      GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      // create user
      val userResult = builderResponse.usersResponse.head.userResult
      val user = userResult.is

      val imsi = "1111"
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, Nil, secondaryIndex = imsi), providerName)
      val claimingTags = List("ah que coucou", "test")
      val newDescription = "newDescription"
      user.claimDevice(imsi, "imsi", claimingTags, "imsi", newDescription)

      val deviceClaimed = DeviceFactory.getBySecondaryIndex(imsi, "imsi").toResourceRepresentation
      deviceClaimed.resource.isClaimed() shouldBe true

      val newDevice = deviceClaimed.unclaimDevice()
      newDevice.resource.isClaimed() shouldBe false
      val attributes = newDevice.getAttributesScala
      attributes.get(Elements.CLAIMING_TAGS_NAME) shouldBe None
      attributes.get(Elements.FIRST_CLAIMED_TIMESTAMP) shouldBe None

      val newGroups = newDevice.resource.getAllGroups()
      newGroups.exists(g => g.getName.equalsIgnoreCase(Elements.UNCLAIMED_DEVICES_GROUP_NAME)) shouldBe true
      newGroups.exists(g => g.getName.toLowerCase.startsWith(Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX.toLowerCase())) shouldBe false
      newGroups.exists(g => g.getName.toLowerCase.startsWith(Elements.PREFIX_OWN_DEVICES.toLowerCase())) shouldBe false
      newGroups.exists(g => g.getName.toLowerCase.startsWith(Elements.PROVIDER_GROUP_PREFIX.toLowerCase())) shouldBe true
      newGroups.exists(g => g.getName.toLowerCase.startsWith(Elements.CLAIMED.toLowerCase())) shouldBe false
    }

  }

  feature("password strategy") {

    scenario("should get from group") {
      val devicePwdGroup = "A_PASSWORD_GROUP"
      val devicePwdUser = "A_PASSWORD_USER"
      val user: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
      val device: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", canBeDeleted = true)
      val groupPasswordName = Elements.DEFAULT_PASSWORD_GROUP_PREFIX + "test"
      val userAttrPwd = Map(Elements.DEFAULT_PASSWORD_USER_ATTRIBUTE -> List(devicePwdUser).asJava).asJava
      val userDevice = UserDevices(user, maybeDevicesShould = Some(List(device)), Some(List(groupPasswordName)), Some(userAttrPwd))
      val users = Option(UsersDevices(List(userDevice)))
      val apiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
      val deviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
      val groupPasswordAttributes = Map(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE -> List(devicePwdGroup).asJava).asJava
      val groupPasswordWithAttributes = GroupWithAttribute(groupPasswordName, groupPasswordAttributes)
      val confGroups = Option(GroupsWithAttribute(List(apiConfGroup, deviceGroup, groupPasswordWithAttributes)))

      val customKeycloakBuilder = InitKeycloakBuilder(users = users, defaultGroups = confGroups)
      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(customKeycloakBuilder)

      val newDevice = keycloakBuilderResponse.usersResponse.head.devicesResult.head.is

      // get password from attributes and verify it
      println(newDevice.getAttributesScala)
      val attributesApiGroup = newDevice.getAttributesScala("attributesApiGroup")
      val json = parse(attributesApiGroup.head)
      implicit val formats: DefaultFormats.type = DefaultFormats
      val pwdStoredInDeviceApiAttributesGroup = (json \ "password").extract[String]
      pwdStoredInDeviceApiAttributesGroup shouldBe devicePwdGroup

      // try to log in to verify that the password is also correctly assigned to the device
      Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(devicePwdGroup.getBytes())) != null shouldBe true
    }

    scenario("should not get from group, but from user assigned password") {
      val devicePwd = "A_PASSWORD_GROUP"
      val devicePwdUser = "A_PASSWORD_USER"
      val user: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
      val device: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", canBeDeleted = true)
      val groupPasswordName = "test" + Elements.DEFAULT_PASSWORD_GROUP_PREFIX
      val userAttrPwd = Map(Elements.DEFAULT_PASSWORD_USER_ATTRIBUTE -> List(devicePwdUser).asJava).asJava
      val userDevice = UserDevices(user, maybeDevicesShould = Some(List(device)), Some(List(groupPasswordName)), Some(userAttrPwd))
      val users = Option(UsersDevices(List(userDevice)))
      val apiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
      val deviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
      val groupPasswordAttributes = Map(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE -> List(devicePwd).asJava).asJava
      val groupPasswordWithAttributes = GroupWithAttribute(groupPasswordName, groupPasswordAttributes)
      val confGroups = Option(GroupsWithAttribute(List(apiConfGroup, deviceGroup, groupPasswordWithAttributes)))

      val customKeycloakBuilder = InitKeycloakBuilder(users = users, defaultGroups = confGroups)
      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(customKeycloakBuilder)

      val newDevice = keycloakBuilderResponse.usersResponse.head.devicesResult.head.is

      // get password from user group
      val correctPwd = keycloakBuilderResponse.usersResponse.head.userResult.is.getPasswordForDevice()

      // get password from attributes and verify that it's not the one from the group but from the user own generated value
      println(newDevice.getAttributesScala)
      val attributesApiGroup = newDevice.getAttributesScala("attributesApiGroup")
      val json = parse(attributesApiGroup.head)
      implicit val formats: DefaultFormats.type = DefaultFormats
      val pwdStoredInDeviceApiAttributesGroup = (json \ "password").extract[String]
      pwdStoredInDeviceApiAttributesGroup != devicePwd shouldBe true
      pwdStoredInDeviceApiAttributesGroup shouldBe correctPwd

      // try to log in to verify that the password is also correctly assigned to the device
      assertThrows[NotAuthorized](Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(devicePwd.getBytes())))
      Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(correctPwd.getBytes())) != null shouldBe true
    }

    scenario("should not get from group, but from user assigned password, same pwd for 2 devices") {
      val devicePwd = "A_PASSWORD_GROUP"
      val devicePwdUser = "A_PASSWORD_USER"
      val user: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
      val device: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", canBeDeleted = true)
      val device2: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", canBeDeleted = true)
      val groupPasswordName = "test" + Elements.DEFAULT_PASSWORD_GROUP_PREFIX
      val userAttrPwd = Map(Elements.DEFAULT_PASSWORD_USER_ATTRIBUTE -> List(devicePwdUser).asJava).asJava
      val userDevice = UserDevices(user, maybeDevicesShould = Some(List(device, device2)), Some(List(groupPasswordName)), Some(userAttrPwd))
      val users = Option(UsersDevices(List(userDevice)))
      val apiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
      val deviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
      val groupPasswordAttributes = Map(Elements.DEFAULT_PASSWORD_GROUP_ATTRIBUTE -> List(devicePwd).asJava).asJava
      val groupPasswordWithAttributes = GroupWithAttribute(groupPasswordName, groupPasswordAttributes)
      val confGroups = Option(GroupsWithAttribute(List(apiConfGroup, deviceGroup, groupPasswordWithAttributes)))

      val customKeycloakBuilder = InitKeycloakBuilder(users = users, defaultGroups = confGroups)
      val keycloakBuilderResponse = TestRefUtil.initKeycloakDeviceUser(customKeycloakBuilder)

      val newDevice = keycloakBuilderResponse.usersResponse.head.devicesResult.head.is
      val newDevice2 = keycloakBuilderResponse.usersResponse.head.devicesResult.tail.head.is

      // get password from user group
      val correctPwd = keycloakBuilderResponse.usersResponse.head.userResult.is.getPasswordForDevice()

      // get password from attributes and verify that it's not the one from the group but from the user own generated value
      println(newDevice.getAttributesScala)
      val attributesApiGroup = newDevice.getAttributesScala("attributesApiGroup")
      val json = parse(attributesApiGroup.head)
      implicit val formats: DefaultFormats.type = DefaultFormats
      val pwdStoredInDeviceApiAttributesGroup = (json \ "password").extract[String]
      pwdStoredInDeviceApiAttributesGroup != devicePwd shouldBe true
      pwdStoredInDeviceApiAttributesGroup shouldBe correctPwd

      println(newDevice2.getAttributesScala)
      val attributesApiGroup2 = newDevice2.getAttributesScala("attributesApiGroup")
      val json2 = parse(attributesApiGroup2.head)
      val pwdStoredInDeviceApiAttributesGroup2 = (json2 \ "password").extract[String]
      pwdStoredInDeviceApiAttributesGroup2 != devicePwd shouldBe true
      pwdStoredInDeviceApiAttributesGroup2 shouldBe correctPwd

      // try to log in to verify that the password is also correctly assigned to the device
      assertThrows[NotAuthorized](Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(devicePwd.getBytes())))
      Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(correctPwd.getBytes())) != null shouldBe true

      // try to log in to verify that the password is also correctly assigned to the device
      assertThrows[NotAuthorized](Auth.auth(newDevice.getHwDeviceId, Base64.getEncoder.encodeToString(devicePwd.getBytes())))
      Auth.auth(newDevice2.getHwDeviceId, Base64.getEncoder.encodeToString(correctPwd.getBytes())) != null shouldBe true
    }
  }

  feature("get device representation") {
    scenario("by KC id") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderPassword)
      val deviceIsAndShould = builderResponse.usersResponse.head.devicesResult.head
      val devicePwd = Some(builderResponse.usersResponse.head.userResult.is.getPasswordForDevice())
      val attributesShould = builderResponse.getDefaultGroupsAttributesShould().deviceTypeGroupAttributes ++ TestRefUtil.updateApiConfigGroup(builderResponse.getDefaultGroupsAttributesShould().apiConfigGroupAttributes, devicePwd)

      // test
      deviceIsAndShould.is.getHwDeviceId shouldBe deviceIsAndShould.should.hwDeviceId.toLowerCase
      deviceIsAndShould.is.getDescription shouldBe deviceIsAndShould.should.description
      deviceIsAndShould.is.resource.getOwners().head.toSimpleUser.copy(id = "") shouldBe builderResponse.usersResponse.head.userResult.should
      deviceIsAndShould.is.getPartialGroups.sortBy(_.id) shouldBe Nil
      deviceIsAndShould.is.getAttributesScala shouldBe attributesShould
    }
  }

  feature("update device") {

    scenario("change owner") {
      val newOwnerDefinition = UserDevices(TestRefUtil.giveMeASimpleUser(), None)
      val keycloakBuilder = defaultInitKeycloakBuilder.addUsers(List(newOwnerDefinition))
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(keycloakBuilder)
      val oldOwnerAndDevice: CreatedUserAndDevices = keycloakResponse.usersResponse.head
      val newOwner = keycloakResponse.getUser(newOwnerDefinition.userShould.username).get.userResult.is

      val device = oldOwnerAndDevice.getFirstDeviceIs

      // commit
      device.changeOwnersOfDevice(List(newOwner))

      // verify
      device.resource.getOwners().head.getUsername shouldBe newOwner.getUsername
    }

    scenario("update only owner of device") {
      // create a second user that will be the new owner of the device
      val newOwnerDefinition = UserDevices(TestRefUtil.giveMeASimpleUser(), None)
      val keycloakBuilder = defaultInitKeycloakBuilder.addUsers(List(newOwnerDefinition))
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(keycloakBuilder)

      val newOwner = keycloakResponse.getUser(newOwnerDefinition.userShould.username).get.userResult.is
      val oldOwnerAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head
      val device = oldOwnerAndDevices.getFirstDeviceIs
      val updatedDeviceStruct: DeviceFE = device.toDeviceFE().copy(owner = List(newOwner.toSimpleUser))
      device.updateDevice(updatedDeviceStruct)
      device.getUpdatedMember.resource.getOwners().head.getId shouldBe newOwner.getKeycloakId
    }

    scenario("update only description of device") {
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(defaultInitKeycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs

      val newDescription = "an even cooler description!"
      val updatedDeviceStruct: DeviceFE = d1.toDeviceFE().copy(description = newDescription)
      d1.updateDevice(updatedDeviceStruct)
      d1.getUpdatedMember.representation.getLastName shouldBe newDescription
    }

    scenario("update only attributes") {
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(defaultInitKeycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs
      val d1FE = d1.toDeviceFE()

      val newDConf = Map("attributesDeviceGroup" -> List("truc"))
      val newApiConf = Map("attributesApiGroup" -> List("machin"))
      val newAttribute = Map("coucou" -> List("salut"))
      val newAttributes = newDConf ++ newApiConf ++ newAttribute
      val updatedDeviceStruct = d1FE.copy(attributes = newAttributes)
      d1.updateDevice(updatedDeviceStruct)
      val updatedDevice = d1.getUpdatedMember.resource.toRepresentation
      val dAttrib = updatedDevice.getAttributes.asScala.toMap
      dAttrib.get("attributesApiGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newApiConf("attributesApiGroup").head
        case None => fail()
      }
      dAttrib.get("attributesDeviceGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newDConf("attributesDeviceGroup").head
        case None => fail
      }
      dAttrib.get("coucou") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newAttribute("coucou").head
        case None => fail
      }
      dAttrib.size shouldBe 3
    }

    scenario("update only device type") {
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(defaultInitKeycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs

      val newDeviceTypeName = "new_device"
      TestRefUtil.createSimpleGroup(
        Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName
      )
      val addDeviceStruct = AddDevice(d1.getUsername, d1.getLastName, newDeviceTypeName, Nil)

      val updatedDevice = d1.updateDevice(
        d1.toDeviceFE().copy(deviceType = newDeviceTypeName)
      )
      updatedDevice.getUpdatedMember.resource.getDeviceType() shouldBe newDeviceTypeName
    }

    scenario("add an additional owner") {
      val newOwnerDefinition = UserDevices(TestRefUtil.giveMeASimpleUser(), None)
      val keycloakBuilder = defaultInitKeycloakBuilder.addUsers(List(newOwnerDefinition))
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(keycloakBuilder)

      val oldOwnerAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head
      val newOwner = keycloakResponse.getUser(newOwnerDefinition.userShould.username).get.userResult.is

      val d1 = oldOwnerAndDevices.getFirstDeviceIs

      val owner1 = oldOwnerAndDevices.userResult.is

      val addDeviceStruct = AddDevice(d1.getUsername, d1.getLastName, d1.resource.getDeviceType(), List.empty)
      d1.updateDevice(
        d1.toDeviceFE().copy(owner = List(owner1.toSimpleUser, newOwner.toSimpleUser))
      )
      d1.getUpdatedMember.resource.getOwners().map { o => o.getId }.sorted shouldBe List(owner1.getKeycloakId, newOwner.getKeycloakId).sorted
    }

    scenario("completely new owner set") {
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(defaultInitKeycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs
      // new user
      val owner2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(Util.getDeviceGroupNameFromUserName(owner2.getUsername))
      owner2.joinGroupById(newGroup.id)

      d1.updateDevice(
        d1.toDeviceFE().copy(owner = List(owner2.toSimpleUser))
      )
      d1.getUpdatedMember.resource.getOwners().map { o => o.getId }.sorted shouldBe List(owner2.getKeycloakId).sorted
    }

    scenario("same owner") {
      // here we create another user to assert that, even with two users in keycloak, only the specified one will be
      // registered as the owner of the device
      val newUserDefinition = UserDevices(TestRefUtil.giveMeASimpleUser(), None)
      val keycloakBuilder = defaultInitKeycloakBuilder.addUsers(List(newUserDefinition))
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(keycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs

      val owner = usersAndDevices.userResult.is

      d1.updateDevice(
        d1.toDeviceFE()
      )
      d1.getUpdatedMember.resource.getOwners().map { o => o.getId }.sorted shouldBe List(owner.getKeycloakId).sorted
    }

    scenario("update everything") {
      val keycloakResponse: InitKeycloakResponse = TestRefUtil.initKeycloakDeviceUser(defaultInitKeycloakBuilder)
      val usersAndDevices: CreatedUserAndDevices = keycloakResponse.usersResponse.head

      val d1 = usersAndDevices.getFirstDeviceIs
      val newGroup = TestRefUtil.createSimpleGroup("newGroup")
      // description
      val newDescription = "an even cooler description!"
      // device type
      val newDeviceTypeName = "new_device"
      TestRefUtil.createSimpleGroup(Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName)
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        newDescription,
        newDeviceTypeName,
        List(newGroup.representation.getName)
      )
      // new user
      val u2 = TestRefUtil.createSimpleUser()
      val newUserGroup = TestRefUtil.createSimpleGroup(
        Elements.PREFIX_OWN_DEVICES + u2.getUsername
      )
      u2.joinGroupById(newUserGroup.id)
      // conf
      val newDConf = Map("test" -> List("truc"))
      val newApiConf = Map("bidule" -> List("machin", "trucmuch"), "ehhhh" -> List("ahhhh"))
      var attributes = scala.collection.mutable.Map(d1.toDeviceFE().attributes.toSeq: _*)
      attributes -= "attributesApiGroup"
      attributes ++= newDConf
      attributes -= "attributesDeviceGroup"
      attributes ++= newApiConf
      val newAttributes = attributes.toMap
      d1.updateDevice(d1.toDeviceFE().copy(
        owner = List(u2.toSimpleUser),
        description = newDescription,
        deviceType = newDeviceTypeName,
        groups = List(newGroup.toGroupFE),
        attributes = newAttributes
      ))

      val updatedDeviceResource = d1.getUpdatedMember

      val updatedDevice = updatedDeviceResource.representation
      val dAttrib = updatedDevice.getAttributes.asScala.toMap
      dAttrib.get("test") match {
        case Some(v) =>
          v.size shouldBe 1
          v.toArray() shouldBe newDConf("test").toArray
        case None => fail()
      }
      dAttrib.get("bidule") match {
        case Some(v) =>
          v.size shouldBe 2
          v.toArray() shouldBe newApiConf("bidule").toArray
        case None => fail
      }
      dAttrib.get("ehhhh") match {
        case Some(v) =>
          v.size shouldBe 1
          v.toArray() shouldBe newApiConf("ehhhh").toArray
        case None => fail
      }
      updatedDeviceResource.resource.getDeviceType() shouldBe newDeviceTypeName
      updatedDeviceResource.getPartialGroups.exists(x => x.id.equalsIgnoreCase(newGroup.id)) shouldBe true
      updatedDeviceResource.resource.getOwners().head.getId shouldBe u2.getKeycloakId
    }
  }

  // the difference with before is that here the device are not gotten with the is, but with the factory
  feature("get device") {
    scenario("get by hwDeviceID") {
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(initKeycloakBuilderPassword)
      val deviceIsAndShould = builderResponse.usersResponse.head.devicesResult.head
      val devicePwd = Some(builderResponse.usersResponse.head.userResult.is.getPasswordForDevice())
      val attributesShould = builderResponse.getDefaultGroupsAttributesShould().deviceTypeGroupAttributes ++ TestRefUtil.updateApiConfigGroup(builderResponse.getDefaultGroupsAttributesShould().apiConfigGroupAttributes, devicePwd)

      DeviceFactory.getByHwDeviceIdQuick(deviceIsAndShould.should.hwDeviceId) match {
        case Left(_) => fail("Device should be found")
        case Right(device) =>
          val newD = device.toResourceRepresentation
          newD.getUsername shouldBe deviceIsAndShould.should.hwDeviceId.toLowerCase
          newD.getLastName shouldBe deviceIsAndShould.should.description
          newD.resource.getOwners().head.toSimpleUser.copy(id = "") shouldBe builderResponse.usersResponse.head.userResult.should
          newD.getPartialGroups.sortBy(_.id) shouldBe Nil
          newD.getAttributesScala shouldBe attributesShould
      }
    }
  }

  feature("full tests") {
    scenario("An admin import some data, a user claim it, the devices logs in") {
      // create (without using endpoint)
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      val builderResponse = TestRefUtil.initKeycloakDeviceUser(PopulateRealm.defaultInitKeycloakBuilder)
      val userAdmin = builderResponse.getUser("chrisx").get.userResult.is
      val provider = "test_provider"
      val uuid = giveMeRandomUUID
      val imsi = "000000000000001"
      val attributes: Map[String, List[String]] = Map(
        "batch_type" -> List("sim_import"),
        "data_hash" -> List("a0f594c1dfdecbc0"),
        "filename" -> List("test.csv"),
        "identity_id" -> List("aaaaa"),
        "import_tags" -> List("coucou", "salut"),
        "imsi" -> List(s"imsi_${imsi}_imsi"),
        "pin" -> List("1234")
      )
      val deviceToAdd = AddDevice(
        hwDeviceId = uuid,
        description = "a description",
        deviceType = "default_type",
        listGroups = Nil,
        attributes = attributes,
        secondaryIndex = "imsi_" + imsi + "_imsi"
      )
      val futureRes = userAdmin.createDeviceAdminAsync(deviceToAdd, provider)
      val res = Await.result(futureRes, 1.minute)

      // claim
      val newDevice = AddDevice(
        hwDeviceId = uuid,
        description = "coucou",
        secondaryIndex = imsi
      )

      val req = BulkRequest("claim", List("tag1", "tag2"), Some("prefix"), List(newDevice))

      userAdmin.claimDevice(SIM.IMSI_PREFIX + req.devices.head.secondaryIndex + SIM.IMSI_SUFFIX, req.prefix.getOrElse(""), req.tags, SIM.IMSI.name, req.devices.head.description)

      // device check groups claim
      val device = DeviceFactory.getBySecondaryIndex(imsi, "IMSI").toResourceRepresentation
      val deviceGroups: List[GroupRepresentation] = device.resource.getAllGroups()
      deviceGroups.exists(p => p.getName == Elements.CLAIMED + provider) shouldBe true
      deviceGroups.exists(p => p.getName == Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX + "chrisx") shouldBe true
      deviceGroups.exists(p => p.getName == Elements.PREFIX_OWN_DEVICES + "chrisx") shouldBe true
      deviceGroups.exists(p => p.getName == Elements.PROVIDER_GROUP_PREFIX + provider) shouldBe true

      implicit val formats = Serialization.formats(NoTypeHints)

      val pwd = (parse(device.getAttributesScala("attributesApiGroup").head) \ "password").extract[String]
      println(s"pwd device = $pwd")

      // device log in
      Auth.auth(uuid, Base64.getEncoder.encodeToString(pwd.getBytes()))
    }

  }

}
