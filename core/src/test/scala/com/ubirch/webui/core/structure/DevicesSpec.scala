package com.ubirch.webui.core.structure

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.BadOwner
import com.ubirch.webui.core.structure.group.Group
import com.ubirch.webui.core.structure.member.{Device, DeviceCreationSuccess, UserFactory}
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class DevicesSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = Util.getRealm
  val DEFAULT_DESCRIPTION = "a cool description for a cool device"
  val DEFAULT_TYPE = "default_type"
  val DEFAULT_PWD = "password"
  val DEFAULT_ATTRIBUTE_D_CONF = "value1"
  val DEFAULT_ATTRIBUTE_API_CONF = "{\"password\":\"password\"}"
  val DEFAULT_MAP_ATTRIBUTE_D_CONF: util.Map[String, util.List[String]] = Map(
    "attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF).asJava
  ).asJava
  val DEFAULT_MAP_ATTRIBUTE_API_CONF: util.Map[String, util.List[String]] = Map(
    "attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF).asJava
  ).asJava
  val DEFAULT_USERNAME = "username_default"
  val DEFAULT_LASTNAME = "lastname_default"
  val DEFAULT_FIRSTNAME = "firstname_default"

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  feature("create device") {
    scenario("create single device") {
      // vals
      val userStruct =
        SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) =
        TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) =
        createGroupsName(userStruct.username, realmName, deviceType)
      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(
        userGroupName
      )(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

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

      user.createNewDevice(
        AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
      )
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
      val userStruct =
        SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) =
        TestRefUtil.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) =
        createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(
        userGroupName
      )(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

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

      val deviceToAdd =
        AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
      user.createMultipleDevices(List(deviceToAdd)) shouldBe List(
        DeviceCreationSuccess(hwDeviceId)
      )
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

      val (hwDeviceId1, deviceType, deviceDescription1) =
        TestRefUtil.generateDeviceAttributes(
          description = "1a cool description"
        )
      val (hwDeviceId2, _, deviceDescription2) =
        TestRefUtil.generateDeviceAttributes(
          description = "2a cool description"
        )
      val (hwDeviceId3, _, deviceDescription3) =
        TestRefUtil.generateDeviceAttributes(
          description = "3a cool description"
        )
      val (hwDeviceId4, _, deviceDescription4) =
        TestRefUtil.generateDeviceAttributes(
          description = "4a cool description"
        )

      val (userGroupName, apiConfigName, deviceConfName) =
        createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) =
        (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: Group = TestRefUtil.createSimpleGroup(randomGroupName)
      TestRefUtil.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(
        userGroupName
      )(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

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

      val resShouldBe = ourList map { d =>
        DeviceCreationSuccess(d.hwDeviceId)
      }

      res.sortBy(r => r.hwDeviceId) shouldBe resShouldBe.sortBy(
        r => r.hwDeviceId
      )

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

  feature("delete device") {
    scenario("delete existing device") {
      val device = createRandomDevice()
      device.deleteDevice()
      assertThrows[NotFoundException](
        realm.users().get(device.memberId).toRepresentation
      )
    }

    scenario("FAIL - delete existing device from bad user") {
      val device = createRandomDevice()
      val user = TestRefUtil.createSimpleUser()
      assertThrows[BadOwner](user.deleteOwnDevice(device))
    }

  }

  feature("get device representation") {
    scenario("by KC id") {
      val deviceFE = createRandomDevice()
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
        owner.toSimpleUser,
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
      deviceFE.getOwner.toSimpleUser shouldBe deviceFeShouldBe.owner
      deviceFE.getGroups.sortBy(x => x.id) shouldBe deviceFeShouldBe.groups
        .sortBy(x => x.id)
      deviceFE.getAttributes shouldBe deviceFeShouldBe.attributes
    }
  }

  feature("update device") {
    scenario("change owner") {
      val device = createRandomDevice()
      val newOwner = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(
        Util.getDeviceGroupNameFromUserName(newOwner.getUsername)
      )
      newOwner.joinGroup(newGroup)
      val oldOwner = UserFactory.getByUsername(DEFAULT_USERNAME)

      // commit
      device.changeOwnerOfDevice(newOwner, oldOwner)

      // verify
      device.getOwner.getUsername shouldBe newOwner.getUsername
    }

    scenario("update only owner of device") {
      val d1 = createRandomDevice()

      // new user
      val u2 = TestRefUtil.createSimpleUser()
      val newGroup = TestRefUtil.createSimpleGroup(
        Util.getDeviceGroupNameFromUserName(u2.getUsername)
      )
      u2.joinGroup(newGroup)

      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, List.empty)
      d1.updateDevice(
        u2,
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getOwner.memberId shouldBe u2.memberId
    }

    scenario("update only description of device") {
      val d1 = createRandomDevice()

      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val newDescription = "an even cooler description!"
      val addDeviceStruct =
        AddDevice(d1.getUsername, newDescription, d1.getDeviceType, Nil)
      d1.updateDevice(
        owner,
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      d1.getUpdatedDevice.getLastName shouldBe newDescription
    }

    scenario("update only device attributes") {
      val d1 = createRandomDevice()

      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val addDeviceStruct =
        AddDevice(d1.getUsername, d1.getLastName, d1.getDeviceType, Nil)
      val newDConf = "fhriugrbvr"
      val newApiConf = "fuigrgrehvidfbkhvbidvbeirhuuigadifioqihqndsljvbkdsjv"
      d1.updateDevice(owner, addDeviceStruct, newDConf, newApiConf)
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
      val d1 = createRandomDevice()
      val newDeviceTypeName = "new_device"
      TestRefUtil.createSimpleGroup(
        Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName
      )
      val owner = UserFactory.getByUsername(DEFAULT_USERNAME)
      val addDeviceStruct = AddDevice(d1.getUsername, d1.getLastName, newDeviceTypeName, Nil)
      val updatedDevice = d1.updateDevice(
        owner,
        addDeviceStruct,
        DEFAULT_ATTRIBUTE_D_CONF,
        DEFAULT_ATTRIBUTE_API_CONF
      )
      updatedDevice.getUpdatedDevice.getDeviceType shouldBe newDeviceTypeName
    }

    scenario("update everything") {
      val d1 = createRandomDevice()
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
      d1.updateDevice(u2, addDeviceStruct, newDConf, newApiConf)
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
      updatedDeviceResource.getGroups.exists(
        x => x.id.equalsIgnoreCase(newGroup.id)
      ) shouldBe true
      updatedDeviceResource.getOwner.memberId shouldBe u2.memberId
    }
  }

  /*
  return KC id of device
   */
  def createRandomDevice(): Device = {
    // vals
    val userStruct = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)

    val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = DEFAULT_DESCRIPTION)

    val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)
    println(createGroupsName(userStruct.username, realmName, deviceType)._2)

    val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

    val deviceConfigRepresentation = new GroupRepresentation
    deviceConfigRepresentation.setAttributes(attributeDConf)

    // create groups
    val (userGroup, apiConfigGroup, _) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)
    println(apiConfigGroup.name)
    // create user
    val user = TestRefUtil.addUserToKC(userStruct)
    // make user join groups
    user.joinGroup(userGroup)
    user.joinGroup(apiConfigGroup)

    val listGroupsToJoinId = List()
    // create roles
    TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
    val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
    user.addRole(userRole.toRepresentation)
    // create device and return device id
    user.createNewDevice(
      AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
    )
  }

  def createGroupsName(
      username: String,
      realmName: String,
      deviceType: String
  ): (String, String, String) = {
    val userGroupName = Util.getDeviceGroupNameFromUserName(username)
    val apiConfigName = Util.getApiConfigGroupName(realmName)
    val deviceConfName = Util.getDeviceConfigGroupName(deviceType)
    (userGroupName, apiConfigName, deviceConfName)
  }

  def createGroups(
      userGroupName: String
  )(attributeApi: util.Map[String, util.List[String]], apiConfName: String)(
      attributeDevice: util.Map[String, util.List[String]],
      deviceConfName: String
  ): (Group, Group, Group) = {
    val userGroup = TestRefUtil.createSimpleGroup(userGroupName)
    val apiConfigGroup =
      TestRefUtil.createGroupWithConf(attributeApi, apiConfName)
    val deviceConfigGroup =
      TestRefUtil.createGroupWithConf(attributeDevice, deviceConfName)
    (userGroup, apiConfigGroup, deviceConfigGroup)
  }

}
