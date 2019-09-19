package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.Exceptions.{BadOwner, UserNotFound}
import com.ubirch.webui.core.operations.Devices._
import com.ubirch.webui.core.operations.Groups._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.{AddDevice, Device, Elements, User}
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.{GroupResource, RealmResource}
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class DevicesSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = getRealm

  override def beforeEach(): Unit = TestUtils.clearKCRealm

  val DEFAULT_DESCRIPTION = "a cool description for a cool device"

  val DEFAULT_PWD = "password"

  val DEFAULT_ATTRIBUTE_D_CONF = "value1"
  val DEFAULT_ATTRIBUTE_API_CONF = "{\"password\":\"password\"}"
  val DEFAULT_MAP_ATTRIBUTE_D_CONF: util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF).asJava).asJava
  val DEFAULT_MAP_ATTRIBUTE_API_CONF: util.Map[String, util.List[String]] = Map("attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF).asJava).asJava

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

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

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
      TestUtils.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestUtils.createAndGetSimpleRole(Elements.USER)
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      createDevice(ownerId, AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId))
      // verify
      TestUtils.verifyDeviceWasCorrectlyAdded(Elements.DEVICE, hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
        listGroupsToJoinId, deviceDescription)
    }

    scenario("bulk creation of correct device, size = 1") {
      // vals
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")

      val (hwDeviceId, deviceType, deviceDescription) = TestUtils.generateDeviceAttributes(description = "a cool description")

      val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)

      val randomGroupName = "random_group"
      val randomGroup2Name = "random_group_2"

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

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
      TestUtils.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestUtils.createAndGetSimpleRole(Elements.USER)
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      val deviceToAdd = AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)

      bulkCreateDevice(ownerId, List(deviceToAdd)) shouldBe List(Devices.createSuccessDevice(hwDeviceId))
      // verify
      TestUtils.verifyDeviceWasCorrectlyAdded(Elements.DEVICE, hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
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

      val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

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
      TestUtils.createAndGetSimpleRole(Elements.DEVICE)
      val userRole = TestUtils.createAndGetSimpleRole(Elements.USER)
      TestUtils.addRoleToUser(user, userRole.toRepresentation)

      val ourList = List(
        AddDevice(hwDeviceId1, deviceDescription1, deviceType, listGroupsToJoinId),
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
        TestUtils.verifyDeviceWasCorrectlyAdded(Elements.DEVICE, d.hwDeviceId, apiConfigGroup, deviceConfigGroup, userGroupName,
          listGroupsToJoinId, d.description)

      }
    }
  }

  feature("delete device") {
    scenario("delete existing device") {
      val id = createRandomDevice()
      val device = Utils.getKCMemberFromId(id)
      deleteDevice(DEFAULT_USERNAME, device.toRepresentation.getUsername)
      assertThrows[NotFoundException](realm.users().get(id).toRepresentation)
    }

    scenario("FAIL - delete existing device from bad user") {
      val id = createRandomDevice()
      val user = TestUtils.createSimpleUser()
      val device = Utils.getKCMemberFromId(id)
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
      val deviceKC = Utils.getKCMemberFromId(idDevice)
      val owner = Users.getUserByUsername(DEFAULT_USERNAME)
      realm.groups().groups(realmName + Elements.PREFIX_API + "default", 0, 1).get(0)
      realm.groups().groups(Elements.PREFIX_DEVICE_TYPE + "default_type", 0, 1).get(0)
      val deviceFeShouldBe = Device(idDevice, deviceKC.toRepresentation.getUsername, DEFAULT_DESCRIPTION, owner, Nil,
        Utils.getKCMemberFromId(idDevice).toRepresentation.getAttributes.asScala.toMap map { x => x._1 -> x._2.asScala.toList })

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
      val dKC = getKCMemberFromId(d)
      val u2 = TestUtils.createSimpleUser()
      val newGroup = TestUtils.createSimpleGroup(Elements.PREFIX_OWN_DEVICES + u2.toRepresentation.getUsername)
      Groups.addSingleUserToGroup(newGroup.toRepresentation.getId, u2.toRepresentation.getId)
      val oldOwnerId = Utils.getKCMemberFromUsername(DEFAULT_USERNAME).toRepresentation.getId

      // commit
      Devices.changeOwnerOfDevice(d, u2.toRepresentation.getId, oldOwnerId)
      logger.info(u2.toRepresentation.getId)

      // verify
      Devices.getOwnerOfDevice(dKC.toRepresentation.getUsername).toRepresentation.getUsername shouldBe u2.toRepresentation.getUsername
    }

    scenario("update only owner of device") {
      val d1Id = createRandomDevice()
      val d1 = getKCMemberFromId(d1Id).toRepresentation

      // new user
      val u2 = TestUtils.createSimpleUser().toRepresentation
      val newGroup = TestUtils.createSimpleGroup(Elements.PREFIX_OWN_DEVICES + u2.getUsername)
      Groups.addSingleUserToGroup(newGroup.toRepresentation.getId, u2.getId)

      val addDeviceStruct = AddDevice(
        d1.getUsername,
        d1.getLastName,
        getDeviceType(d1Id),
        Nil
      )
      Devices.updateDevice(u2.getId, addDeviceStruct, DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)
      Devices.getOwnerOfDevice(d1.getUsername).toRepresentation.getUsername shouldBe u2.getUsername
    }

    scenario("update only description of device") {
      val d1Id = createRandomDevice()
      val d1 = getKCMemberFromId(d1Id).toRepresentation

      val ownerId = Utils.getKCMemberFromUsername(DEFAULT_USERNAME).toRepresentation.getId
      val newDescription = "an even cooler description!"
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        newDescription,
        getDeviceType(d1Id),
        Nil
      )
      Devices.updateDevice(ownerId, addDeviceStruct, DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)
      val updatedDevice = getKCMemberFromId(d1Id).toRepresentation
      updatedDevice.getLastName shouldBe newDescription
    }

    scenario("update only device attributes") {
      val d1Id = createRandomDevice()
      val d1 = getKCMemberFromId(d1Id).toRepresentation

      val ownerId = Utils.getKCMemberFromUsername(DEFAULT_USERNAME).toRepresentation.getId
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        d1.getLastName,
        getDeviceType(d1Id),
        Nil
      )
      val newDConf = "fhriugrbvr"
      val newApiConf = "fuigrgrehvidfbkhvbidvbeirhuuigadifioqihqndsljvbkdsjv"
      Devices.updateDevice(ownerId, addDeviceStruct, newDConf, newApiConf)
      val updatedDevice = getKCMemberFromId(d1Id).toRepresentation
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
      val d1Id = createRandomDevice()
      val d1 = getKCMemberFromId(d1Id).toRepresentation
      val newDeviceTypeName = "new_device"
      TestUtils.createSimpleGroup(Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName)
      val ownerId = Utils.getKCMemberFromUsername(DEFAULT_USERNAME).toRepresentation.getId
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        d1.getLastName,
        newDeviceTypeName,
        Nil
      )
      Devices.updateDevice(ownerId, addDeviceStruct, DEFAULT_ATTRIBUTE_D_CONF, DEFAULT_ATTRIBUTE_API_CONF)
      val updatedDevice = getKCMemberFromId(d1Id).toRepresentation
      Devices.getDeviceType(updatedDevice.getId) shouldBe newDeviceTypeName
    }

    scenario("update everything") {
      val d1Id = createRandomDevice()
      val d1 = getKCMemberFromId(d1Id).toRepresentation
      val newGroup = TestUtils.createSimpleGroup("newGroup")
      // description
      val newDescription = "an even cooler description!"
      // device type
      val newDeviceTypeName = "new_device"
      TestUtils.createSimpleGroup(Elements.PREFIX_DEVICE_TYPE + newDeviceTypeName)
      val addDeviceStruct = AddDevice(
        d1.getUsername,
        newDescription,
        newDeviceTypeName,
        List(newGroup.toRepresentation.getId)
      )
      // new user
      val u2 = TestUtils.createSimpleUser().toRepresentation
      val newUserGroup = TestUtils.createSimpleGroup(Elements.PREFIX_OWN_DEVICES + u2.getUsername)
      Groups.addSingleUserToGroup(newUserGroup.toRepresentation.getId, u2.getId)
      // conf
      val newDConf = "fhriugrbvr"
      val newApiConf = "fuigrgrehvidfbkhvbidvbeirhuuigadifioqihqndsljvbkdsjv"
      Devices.updateDevice(u2.getId, addDeviceStruct, newDConf, newApiConf)
      val updatedDeviceResource = getKCMemberFromId(d1Id)

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
      Devices.getDeviceType(updatedDevice.getId) shouldBe newDeviceTypeName
      updatedDeviceResource.groups().asScala.toList.exists(x => x.getId.equalsIgnoreCase(newGroup.toRepresentation.getId)) shouldBe true
      Devices.getOwnerOfDevice(d1.getUsername).toRepresentation.getUsername shouldBe u2.getUsername
    }
  }

  def createGroupsName(username: String, realmName: String, deviceType: String): (String, String, String) = {
    val userGroupName = Elements.PREFIX_OWN_DEVICES + username
    val apiConfigName = realmName + Elements.PREFIX_API + "default"
    val deviceConfName = Elements.PREFIX_DEVICE_TYPE + deviceType
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
    println(createGroupsName(userStruct.username, realmName, deviceType)._2)

    val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

    val deviceConfigRepresentation = new GroupRepresentation
    deviceConfigRepresentation.setAttributes(attributeDConf)

    // create groups
    val (userGroup, apiConfigGroup, _) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)
    println(apiConfigGroup.toRepresentation.getName)
    // create user
    val user = TestUtils.addUserToKC(userStruct)
    // make user join groups
    addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
    addSingleUserToGroup(apiConfigGroup.toRepresentation.getId, user.toRepresentation.getId)

    val ownerId = user.toRepresentation.getId
    val listGroupsToJoinId = List()
    // create roles
    TestUtils.createAndGetSimpleRole(Elements.DEVICE)
    val userRole = TestUtils.createAndGetSimpleRole(Elements.USER)
    TestUtils.addRoleToUser(user, userRole.toRepresentation)
    // create device and return device id
    createDevice(ownerId, AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId))
  }

}
