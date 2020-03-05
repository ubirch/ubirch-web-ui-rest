package com.ubirch.webui.core.structure

import com.ubirch.webui.core.{ApiUtil, TestRefUtil}
import com.ubirch.webui.core.Exceptions.BadOwner
import com.ubirch.webui.core.structure.group.{Group, GroupFactory}
import com.ubirch.webui.core.structure.member.{DeviceCreationSuccess, UserFactory}
import com.ubirch.webui.test.EmbeddedKeycloakUtil
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class DevicesSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
  val providerName = "PROVIDER"

  feature("create device") {
    scenario("create single device") {
      // vals
      val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)
      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      // make user join groups
      user.joinGroup(userGroup)
      user.joinGroup(apiConfigGroup)

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      user.createNewDevice(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId))
      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAdded(
        Elements.DEVICE,
        hwDeviceId,
        apiConfigGroup,
        deviceConfigGroup,
        userGroupName,
        listGroupsToJoinId,
        deviceDescription
      )
    }

    scenario("bulk creation of correct device, size = 1") {
      // vals
      val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      // make user join groups
      user.joinGroup(userGroup)
      user.joinGroup(apiConfigGroup)

      val ownerId = user.toRepresentation.getId
      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      val deviceToAdd = AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
      user.createMultipleDevices(List(deviceToAdd)) shouldBe List(DeviceCreationSuccess(hwDeviceId))
      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAdded(
        Elements.DEVICE,
        hwDeviceId,
        apiConfigGroup,
        deviceConfigGroup,
        userGroupName,
        listGroupsToJoinId,
        deviceDescription
      )
    }

    scenario("bulk creation of correct devices, size > 1") {
      // vals
      val userStruct =
        SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId1, deviceType, deviceDescription1) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")
      val (hwDeviceId2, _, deviceDescription2) = TestRefUtil.generateDeviceAttributes(description = "2a cool description")
      val (hwDeviceId3, _, deviceDescription3) = TestRefUtil.generateDeviceAttributes(description = "3a cool description")
      val (hwDeviceId4, _, deviceDescription4) = TestRefUtil.generateDeviceAttributes(description = "4a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      ApiUtil.resetUserPassword(
        user.keyCloakMember,
        "password",
        temporary = false
      )
      // make user join groups
      user.joinGroup(userGroup.id)
      user.joinGroup(apiConfigGroup.id)

      val ownerId = user.toRepresentation.getId
      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      val ourList = List(
        AddDevice(
          hwDeviceId1,
          deviceDescription1,
          deviceType,
          listGroupsToJoinId
        ),
        AddDevice(
          hwDeviceId2,
          deviceDescription2,
          deviceType,
          listGroupsToJoinId
        ),
        AddDevice(
          hwDeviceId3,
          deviceDescription3,
          deviceType,
          listGroupsToJoinId
        ),
        AddDevice(
          hwDeviceId4,
          deviceDescription4,
          deviceType,
          listGroupsToJoinId
        )
      )
      val t0 = System.currentTimeMillis()
      val res = user.createMultipleDevices(ourList)
      val t1 = System.currentTimeMillis()
      println(t1 - t0 + " ms to create devices")
      println("res: " + res)

      val resShouldBe = ourList map { d => DeviceCreationSuccess(d.hwDeviceId) }

      res.sortBy(r => r.hwDeviceId) shouldBe resShouldBe.sortBy(r => r.hwDeviceId)

      // verify
      ourList foreach { d =>
        TestRefUtil.verifyDeviceWasCorrectlyAdded(
          Elements.DEVICE,
          d.hwDeviceId,
          apiConfigGroup,
          deviceConfigGroup,
          userGroupName,
          listGroupsToJoinId,
          d.description
        )
      }
    }
  }

  feature("devices added through the admin process tests") {
    scenario("add 1 device") {

      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)
      val providerGroup = GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      val unclaimedDevicesGroup = GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      // make user join groups
      user.joinGroup(userGroup)
      user.joinGroup(apiConfigGroup)

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId), providerName)

      // verify
      TestRefUtil.verifyDeviceWasCorrectlyAddedAdmin(
        Elements.DEVICE,
        hwDeviceId,
        apiConfigGroup,
        deviceConfigGroup,
        userGroupName,
        listGroupsToJoinId,
        deviceDescription,
        providerName
      )
    }

    scenario("n devices addition: using cache") {
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")
      val (hwDeviceId2, _, deviceDescription2) = TestRefUtil.generateDeviceAttributes(description = "2a cool description")
      val (hwDeviceId3, _, deviceDescription3) = TestRefUtil.generateDeviceAttributes(description = "3a cool description")
      val (hwDeviceId4, _, deviceDescription4) = TestRefUtil.generateDeviceAttributes(description = "4a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)
      val providerGroup = GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      val unclaimedDevicesGroup = GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      // make user join groups
      user.joinGroup(userGroup)
      user.joinGroup(apiConfigGroup)

      val listGroupsToJoinId = List(randomGroupKc.id)
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      val ourList = List(
        AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId2, deviceDescription2, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId3, deviceDescription3, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId4, deviceDescription4, deviceType, listGroupsToJoinId)
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
          apiConfigGroup,
          deviceConfigGroup,
          userGroupName,
          listGroupsToJoinId,
          d.description,
          providerName
        )
      }

    }

    scenario("add one device and claim it") {
      val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)
      val providerGroup = GroupFactory.getOrCreateGroup(Util.getProviderGroupName(providerName))
      val unclaimedDevicesGroup = GroupFactory.getOrCreateGroup(Elements.UNCLAIMED_DEVICES_GROUP_NAME)

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val (userGroup, apiConfigGroup, deviceConfigGroup) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestRefUtil.addUserToKC(userStruct)
      // make user join groups
      user.joinGroup(userGroup)
      user.joinGroup(apiConfigGroup)

      val listGroupsToJoinId = Nil
      // create roles
      TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
      user.addRole(userRole.toRepresentation)

      val imsi = "1111"
      user.createNewDeviceAdmin(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId, secondaryIndex = imsi), providerName)

      user.claimDevice(imsi, "imsi", "imsi")

      // verify
      TestRefUtil.verifyDeviceWasCorrectlyClaimed(
        hwDeviceId,
        apiConfigGroup,
        userStruct.username,
        deviceConfigGroup,
        Nil,
        "imsi" + deviceDescription,
        providerName,
        secondaryIndex = imsi
      )
    }

  }

  feature("delete device") {
    scenario("delete existing device") {
      val device = TestRefUtil.createRandomDevice()
      device.deleteDevice()
      assertThrows[NotFoundException](
        realm.users().get(device.memberId).toRepresentation
      )
    }

    scenario("FAIL - delete existing device from bad user") {
      val device = TestRefUtil.createRandomDevice()
      val user = TestRefUtil.createSimpleUser()
      assertThrows[BadOwner](user.deleteOwnDevice(device))
    }

  }

  feature("get device representation") {
    scenario("by KC id") {
      val deviceFE = TestRefUtil.createRandomDevice()
      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      realm.groups().groups(Util.getApiConfigGroupName(realmName), 0, 1).get(0)
      realm
        .groups()
        .groups(Util.getDeviceConfigGroupName(DEFAULT_TYPE), 0, 1)
        .get(0)
      val deviceFeShouldBe = DeviceFE(
        deviceFE.memberId,
        deviceFE.toRepresentation.getUsername,
        DEFAULT_DESCRIPTION,
        List(owner.toSimpleUser),
        Nil,
        deviceFE.toRepresentation.getAttributes.asScala.toMap map { x =>
          x._1 -> x._2.asScala.toList
        },
        customerId = Util.getCustomerId(realmName)
      )

      // test
      deviceFE.memberId shouldBe deviceFeShouldBe.id
      deviceFE.getHwDeviceId shouldBe deviceFeShouldBe.hwDeviceId
      deviceFE.getDescription shouldBe deviceFeShouldBe.description
      deviceFE.getOwners.head.toSimpleUser shouldBe deviceFeShouldBe.owner.head
      deviceFE.getGroups.sortBy(x => x.id) shouldBe deviceFeShouldBe.groups
        .sortBy(x => x.id)
      deviceFE.getAttributes shouldBe deviceFeShouldBe.attributes
    }
  }

  feature("update device") {
    scenario("change owner") {
      val device = TestRefUtil.createRandomDevice()
      val newOwner = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(
        Util.getDeviceGroupNameFromUserName(newOwner.getUsername)
      )
      newOwner.joinGroup(newGroup)

      // commit
      device.changeOwnersOfDevice(List(newOwner))

      // verify
      device.getOwners.head.getUsername shouldBe newOwner.getUsername
    }

    scenario("update only owner of device") {
      val d1 = TestRefUtil.createRandomDevice()

      // new user
      val u2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(
        Util.getDeviceGroupNameFromUserName(u2.getUsername)
      )
      u2.joinGroup(newGroup)

      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, List.empty)
      d1.updateDevice(
        List(u2),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getOwners.head.memberId shouldBe u2.memberId
    }

    scenario("update only description of device") {
      val d1 = TestRefUtil.createRandomDevice()

      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val newDescription = "an even cooler description!"
      val addDeviceStruct =
        AddDevice(d1.getUsername, newDescription, d1.getDeviceType, Nil)
      d1.updateDevice(
        List(owner),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getLastName shouldBe newDescription
    }

    scenario("update only device attributes") {
      val d1 = TestRefUtil.createRandomDevice()

      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, Nil)
      val newDConf = "fhriugrbvr"
      val newApiConf = "fuigrgrehvidfbkhvbidvbeirhuuigadifioqihqndsljvbkdsjv"
      d1.updateDevice(List(owner), addDeviceStruct, newDConf, newApiConf)
      val updatedDevice = d1.getUpdatedDevice.keyCloakMember.toRepresentation
      val dAttrib = updatedDevice.getAttributes.asScala.toMap
      dAttrib.get("attributesApiGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newApiConf
        case None => fail()
      }
      dAttrib.get("attributesDeviceGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newDConf
        case None => fail
      }
    }

    scenario("update only device type") {
      val d1 = TestRefUtil.createRandomDevice()
      val newDeviceTypeName = "new_device"
      TestRefUtil.createSimpleGroup(
        Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName
      )
      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val addDeviceStruct = AddDevice(d1.getUsername, d1.getLastName, newDeviceTypeName, Nil)
      val updatedDevice = d1.updateDevice(
        List(owner),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      updatedDevice.getUpdatedDevice.getDeviceType shouldBe newDeviceTypeName
    }

    scenario("add an additional owner") {
      val d1 = TestRefUtil.createRandomDevice()
      val owner1 = UserFactory.getByUsername(DEFAULT_USERNAME)
      // new user
      val owner2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(Util.getDeviceGroupNameFromUserName(owner2.getUsername))
      owner2.joinGroup(newGroup)

      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, List.empty)
      d1.updateDevice(
        List(owner1, owner2),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getOwners.map { o => o.memberId }.sorted shouldBe List(owner1.memberId, owner2.memberId).sorted
    }

    scenario("completely new owner set") {
      val d1 = TestRefUtil.createRandomDevice()
      // new user
      val owner2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(Util.getDeviceGroupNameFromUserName(owner2.getUsername))
      owner2.joinGroup(newGroup)

      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, List.empty)
      d1.updateDevice(
        List(owner2),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getOwners.map { o => o.memberId }.sorted shouldBe List(owner2.memberId).sorted
    }

    scenario("same owner") {
      val d1 = TestRefUtil.createRandomDevice()
      val owner1 = UserFactory.getByUsername(DEFAULT_USERNAME)
      // new user
      val owner2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(Util.getDeviceGroupNameFromUserName(owner2.getUsername))
      owner2.joinGroup(newGroup)

      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, List.empty)
      d1.updateDevice(
        List(owner1),
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getOwners.map { o => o.memberId }.sorted shouldBe List(owner1.memberId).sorted
    }

    scenario("update everything") {
      val d1 = TestRefUtil.createRandomDevice()
      val newGroup = TestRefUtil.createSimpleGroup("newGroup")
      // description
      val newDescription = "an even cooler description!"
      // device type
      val newDeviceTypeName = "new_device"
      TestRefUtil.createSimpleGroup(
        Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName
      )
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        newDescription,
        newDeviceTypeName,
        List(newGroup.getRepresentation.getName)
      )
      // new user
      val u2 = TestRefUtil.createSimpleUser()
      val newUserGroup = TestRefUtil.createSimpleGroup(
        Elements.PREFIX_OWN_DEVICES + u2.getUsername
      )
      u2.joinGroup(newUserGroup)
      // conf
      val newDConf = "fhriugrbvr"
      val newApiConf = "fuigrgrehvidfbkhvbidvbeirhuuigadifioqihqndsljvbkdsjv"
      d1.updateDevice(List(u2), addDeviceStruct, newDConf, newApiConf)
      val updatedDeviceResource = d1.getUpdatedDevice

      val updatedDevice = updatedDeviceResource.toRepresentation
      val dAttrib = updatedDevice.getAttributes.asScala.toMap
      dAttrib.get("attributesApiGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newApiConf
        case None => fail()
      }
      dAttrib.get("attributesDeviceGroup") match {
        case Some(v) =>
          v.size shouldBe 1
          v.get(0) shouldBe newDConf
        case None => fail
      }
      updatedDeviceResource.getDeviceType shouldBe newDeviceTypeName
      updatedDeviceResource.getGroups.exists(x => x.id.equalsIgnoreCase(newGroup.id)) shouldBe true
      updatedDeviceResource.getOwners.head.memberId shouldBe u2.memberId
    }
  }

}
