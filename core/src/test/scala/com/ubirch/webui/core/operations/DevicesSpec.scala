package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{BadOwner, UserNotFound}
import com.ubirch.webui.core.operations.Devices._
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{AddDevice, Device, User}
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.{GroupResource, RealmResource}
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class DevicesSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = getRealm

  override def beforeEach(): Unit = TestUtils.clearKCRealm

  val DEFAULT_DESCRIPTION = "a cool description for a device"

  val API_GROUP_PART_NAME = "_apiConfigGroup_default"
  val DEVICE_GROUP_PART_NAME = "_DeviceConfigGroup"
  val USER_DEVICE_PART_NAME = "_OWN_DEVICES"

  val DEFAULT_PWD = "password"

  val DEFAULT_ATTRIBUTE_D_CONF: util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List("value1").asJava).asJava
  val DEFAULT_ATTRIBUTE_API_CONF: util.Map[String, util.List[String]] = Map("attributesApiGroup" -> List("{\"password\":\"password\"}").asJava).asJava

  val DEFAULT_USERNAME = "username_default"
  val DEFAULT_LASTNAME = "lastname_default"
  val DEFAULT_FIRSTNAME = "firstname_default"

  feature("create device") {
    scenario("create single device") {
      // vals
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) = TestUtils.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)
      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)


      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: GroupResource = TestUtils.createSimpleGroup(randomGroupName)
      TestUtils.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestUtils.addUserToKC(userStruct)
      // make user join groups
      addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
      addSingleUserToGroup(apiConfigGroup.toRepresentation.getId, user.toRepresentation.getId)

      val ownerId = user.toRepresentation.getId
      val listGroupsToJoinId = List(randomGroupKc.toRepresentation.getId)
      // create roles
      TestUtils.createAndGetSimpleRole("DEVICE")
      val userRole = TestUtils.createAndGetSimpleRole("USER")
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      createDevice(ownerId, AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId))
      // verify
      TestUtils.verifyDeviceWasCorrectlyAdded("DEVICE", hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
        listGroupsToJoinId, deviceDescription)
    }

    scenario("bulk creation of correct device, size = 1") {
      // vals
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) = TestUtils.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)


      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: GroupResource = TestUtils.createSimpleGroup(randomGroupName)
      TestUtils.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestUtils.addUserToKC(userStruct)
      // make user join groups
      addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
      addSingleUserToGroup(apiConfigGroup.toRepresentation.getId, user.toRepresentation.getId)

      val ownerId = user.toRepresentation.getId
      val listGroupsToJoinId = List(randomGroupKc.toRepresentation.getId)
      // create roles
      TestUtils.createAndGetSimpleRole("DEVICE")
      val userRole = TestUtils.createAndGetSimpleRole("USER")
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      val deviceToAdd = AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)

      bulkCreateDevice(ownerId, List(deviceToAdd)) shouldBe List(Devices.createSuccessDevice(hwDeviceId))
      // verify
      TestUtils.verifyDeviceWasCorrectlyAdded("DEVICE", hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
        listGroupsToJoinId, deviceDescription)
    }

    scenario("bulk creation of correct devices, size > 1") {
      // vals
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId1, deviceType, deviceDescription1) = TestUtils.generateDeviceAttributes(description = "1a cool description")
      val (hwDeviceId2, _, deviceDescription2) = TestUtils.generateDeviceAttributes(description = "2a cool description")
      val (hwDeviceId3, _, deviceDescription3) = TestUtils.generateDeviceAttributes(description = "3a cool description")
      val (hwDeviceId4, _, deviceDescription4) = TestUtils.generateDeviceAttributes(description = "4a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)

      val deviceConfigRepresentation = new GroupRepresentation
      deviceConfigRepresentation.setAttributes(attributeDConf)
      // create groups
      val randomGroupKc: GroupResource = TestUtils.createSimpleGroup(randomGroupName)
      TestUtils.createSimpleGroup(randomGroup2Name)
      val (userGroup, apiConfigGroup, deviceConfigGroup) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

      // create user
      val user = TestUtils.addUserToKC(userStruct)
      ApiUtil.resetUserPassword(user, "password", temporary = false)
      // make user join groups
      addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
      addSingleUserToGroup(apiConfigGroup.toRepresentation.getId, user.toRepresentation.getId)

      val ownerId = user.toRepresentation.getId
      val listGroupsToJoinId = List(randomGroupKc.toRepresentation.getId)
      // create roles
      TestUtils.createAndGetSimpleRole("DEVICE")
      val userRole = TestUtils.createAndGetSimpleRole("USER")
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      val ourList = List(AddDevice(hwDeviceId1, deviceDescription1, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId2, deviceDescription2, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId3, deviceDescription3, deviceType, listGroupsToJoinId),
        AddDevice(hwDeviceId4, deviceDescription4, deviceType, listGroupsToJoinId)
      )
      val t0 = System.currentTimeMillis()
      val res = bulkCreateDevice(ownerId, ourList)
      val t1 = System.currentTimeMillis()
      println(t1 - t0 + " ms to create devices")
      println("res: " + res)

      val resShouldBe = ourList map { d => Devices.createSuccessDevice(d.hwDeviceId) }

      res.sorted shouldBe resShouldBe.sorted

      // verify
      ourList foreach { d =>
        TestUtils.verifyDeviceWasCorrectlyAdded("DEVICE", d.hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
          listGroupsToJoinId, d.description)

      }
    }
  }

  feature("delete device") {
    scenario("delete existing device") {
      val id = createRandomDevice()
      val device = Utils.getKCUserFromId(id)
      deleteDevice(DEFAULT_USERNAME, device.toRepresentation.getUsername)
      assertThrows[NotFoundException](realm.users().get(id).toRepresentation)
    }

    scenario("FAIL - delete existing device from bad user") {
      val id = createRandomDevice()
      val user = TestUtils.createSimpleUser()
      val device = Utils.getKCUserFromId(id)
      assertThrows[BadOwner](deleteDevice(user.toRepresentation.getUsername, device.toRepresentation.getUsername))
    }

    scenario("delete non existing device") {
      val id = "super_random_id"
      assertThrows[UserNotFound](deleteDevice(DEFAULT_USERNAME, id))
    }
  }

  feature("get device representation") {
    scenario("by KC id") {
      val idDevice = createRandomDevice()
      val deviceFE = getDeviceByInternalKcId(idDevice)
      println(deviceFE)
      val deviceKC = Utils.getKCUserFromId(idDevice)
      val owner = Users.getUserByUsername(DEFAULT_USERNAME)
      val apiConfigGroup = realm.groups().groups(realmName + API_GROUP_PART_NAME, 0, 1).get(0)
      val deviceConfigGroup = realm.groups().groups("default_type" + DEVICE_GROUP_PART_NAME, 0, 1).get(0)
      val deviceFeShouldBe = Device(idDevice, deviceKC.toRepresentation.getUsername, DEFAULT_DESCRIPTION, owner, Nil,
        Utils.getKCUserFromId(idDevice).toRepresentation.getAttributes.asScala.toMap map { x => x._1 -> x._2.asScala.toList })

      // test
      deviceFE.id shouldBe deviceFeShouldBe.id
      deviceFE.hwDeviceId shouldBe deviceFeShouldBe.hwDeviceId
      deviceFE.description shouldBe deviceFeShouldBe.description
      deviceFE.owner shouldBe deviceFeShouldBe.owner
      deviceFE.groups.sortBy(x => x.id) shouldBe deviceFeShouldBe.groups.sortBy(x => x.id)
      deviceFE.attributes shouldBe deviceFeShouldBe.attributes
    }
  }

  feature("update device") {
    scenario("change owner") {
      val d = createRandomDevice()
      val dKC = getKCUserFromId(d)
      val u2 = TestUtils.createSimpleUser()
      val newGroup = TestUtils.createSimpleGroup(u2.toRepresentation.getUsername + USER_DEVICE_PART_NAME)
      Groups.addSingleUserToGroup(newGroup.toRepresentation.getId, u2.toRepresentation.getId)
      val oldOwnerId = Utils.getKCUserFromUsername(DEFAULT_USERNAME).toRepresentation.getId

      // commit
      Devices.changeOwnerOfDevice(d, u2.toRepresentation.getId, oldOwnerId)
      logger.info(u2.toRepresentation.getId)

      // verify
      Devices.getOwnerOfDevice(dKC.toRepresentation.getUsername).toRepresentation.getUsername shouldBe u2.toRepresentation.getUsername
    }

    scenario("update only user") {
      //      val (d1Id, d2Id) = (createRandomDevice(), createRandomDevice())
      //      val (d1, d2) = (getKCUserFromId(d1Id), getKCUserFromId(d2Id))
      //
      //      // new user
      //      val u2 = TestUtils.createSimpleUser()
      //      val newGroup = TestUtils.createSimpleGroup(u2.toRepresentation.getUsername + USER_DEVICE_PART_NAME)
      //      Groups.addSingleUserToGroup(newGroup.toRepresentation.getId, u2.toRepresentation.getId)
      //      val oldOwnerId = Utils.getKCUserFromUsername(DEFAULT_USERNAME).toRepresentation.getId

      // new attributes
      //val addDeviceStruct = AddDevice(d1.toRepresentation.getUsername, d1.toRepresentation.getLastName, getDeviceType(d1Id), getGroupsOfAUser())

    }
  }

  def createGroupsName(username: String, realmName: String, deviceType: String): (String, String, String) = {
    val userGroupName = username + USER_DEVICE_PART_NAME
    val apiConfigName = realmName + API_GROUP_PART_NAME
    val deviceConfName = deviceType + DEVICE_GROUP_PART_NAME
    (userGroupName, apiConfigName, deviceConfName)
  }

  def createGroups(userGroupName: String)
                  (attributeApi: util.Map[String, util.List[String]], apiConfName: String)
                  (attributeDevice: util.Map[String, util.List[String]], deviceConfName: String): (GroupResource, GroupResource, GroupResource) = {
    val userGroup = TestUtils.createSimpleGroup(userGroupName)
    val apiConfigGroup = TestUtils.createGroupWithConf(attributeApi, apiConfName)
    val deviceConfigGroup = TestUtils.createGroupWithConf(attributeDevice, deviceConfName)
    (userGroup, apiConfigGroup, deviceConfigGroup)
  }

  /*
  return KC id of device
   */
  def createRandomDevice(): String = {
    // vals
    val userStruct = User("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)

    val (hwDeviceId, deviceType, deviceDescription) = TestUtils.generateDeviceAttributes(description = DEFAULT_DESCRIPTION)

    val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)

    val (attributeDConf, attributeApiConf) = (DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)

    val deviceConfigRepresentation = new GroupRepresentation
    deviceConfigRepresentation.setAttributes(attributeDConf)

    // create groups
    val (userGroup, apiConfigGroup, _) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

    // create user
    val user = TestUtils.addUserToKC(userStruct)
    // make user join groups
    addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
    addSingleUserToGroup(apiConfigGroup.toRepresentation.getId, user.toRepresentation.getId)

    val ownerId = user.toRepresentation.getId
    val listGroupsToJoinId = List()
    // create roles
    TestUtils.createAndGetSimpleRole("DEVICE")
    val userRole = TestUtils.createAndGetSimpleRole("USER")
    TestUtils.addRoleToUser(user, userRole.toRepresentation)
    // create device and return device id
    createDevice(ownerId, AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId))
  }


}
