package com.ubirch.webui.models.keycloak

import com.ubirch.webui.{ EmbeddedKeycloakUtil, _ }
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member.{ DeviceCreationState, DeviceFactory, UserFactory }
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, QuickActions, Util }
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.Exceptions.GroupNotEmpty
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

import scala.collection.JavaConverters._
import scala.concurrent.Await

class GroupsSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  //override def afterAll(): Unit = stopEmbeddedKeycloak()

  feature("leave group") {
    scenario("user should leave group that he belongs to") {
      // vals
      val username = "username_leave_group_1"
      val firstname = "firstname_leave_group_1"
      val lastname = "lastname_leave_group_1"
      val groupName = "groupname_leave_group_1"

      // test
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      val group: GroupResourceRepresentation = TestRefUtil.createSimpleGroup(groupName)
      user.joinGroup(group.id)
      group.getMembers.head.getFirstName shouldBe firstname
      user.leaveGroup(group.representation)
      group.getMembers.size shouldBe 0
    }

    scenario(
      "user should not be able to leave he group that he doesn't belong to"
    ) {
        // vals
        val username = "username_leave_group_2"
        val firstname = "firstname_leave_group_2"
        val lastname = "lastname_leave_group_2"
        val groupName = "groupname_leave_group_2"
        val groupname2 = "dummy_group"

        // test
        val user = TestRefUtil.addUserToKC(username, firstname, lastname)
        val group = TestRefUtil.createSimpleGroup(groupName)
        val dummyGroup = TestRefUtil.createSimpleGroup(groupname2)
        user.joinGroup(group.id)
        group.getMembers.head.getFirstName shouldBe firstname
        try {
          user.leaveGroup(dummyGroup.representation)
        } catch {
          case e: Exception => println(e.getMessage)
        }
        group.getMembers.size shouldBe 1
      }
  }

  feature("delete group") {
    scenario("delete existing group") {
      // vals
      val groupName = "groupname_delete_1"

      // test
      val group = TestRefUtil.createSimpleGroup(groupName)
      group.deleteGroup()
      realm.groups().count().get("count") shouldBe 0
    }

    scenario("delete group with a user inside -> FAIL") {
      // vals
      val username = "username_leave_group_2"
      val firstname = "firstname_leave_group_2"
      val lastname = "lastname_leave_group_2"
      val groupName = "groupname_leave_group_2"
      //test
      val group = TestRefUtil.createSimpleGroup(groupName)
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      user.joinGroup(group.id)
      assertThrows[GroupNotEmpty](group.deleteGroup())
    }
  }

  feature("is a group empty") {
    scenario("empty group") {
      // vals
      val groupName = "groupname_empty_1"
      // test
      val group = TestRefUtil.createSimpleGroup(groupName)
      group.resource.isEmpty shouldBe true
    }

    scenario("non empty group") {
      // vals
      val username = "username_empty_group_2"
      val firstname = "firstname_empty_group_2"
      val lastname = "lastname_empty_group_2"
      val groupName = "groupname_empty_group_2"
      //test
      val group = TestRefUtil.createSimpleGroup(groupName)
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      user.joinGroup(group.id)
      group.resource.isEmpty shouldBe false
    }

  }

  feature("list all users of group") {
    scenario("group contains only one users") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group = TestRefUtil.createSimpleGroup(groupName)
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      val roleName = Elements.USER
      // create role
      val role = TestRefUtil.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new java.util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      user.resource.roles().realmLevel().add(roleRepresentationList)
      user.joinGroup(group.id)
      val userFE =
        SimpleUser(user.getKeycloakId, username, lastname, firstname)
      group.getMembers.head.getUsername shouldBe userFE.username
    }

    scenario("group contains users and devices") {
      // vals
      val usernameU1 = "username_list_users_2.1"
      val firstnameU1 = "firstname_list_users_2.1"
      val lastnameU1 = "lastname_list_users_2.1"

      val usernameU2 = "username_list_users_2.2"
      val firstnameU2 = "firstname_list_users_2.2"
      val lastnameU2 = "lastname_list_users_2.2"

      val usernameU3 = "username_list_users_2.3"
      val firstnameU3 = "firstname_list_users_2.3"
      val lastnameU3 = "lastname_list_users_2.3"

      val usernameD1 = "username_list_users_2.4"
      val firstnameD1 = "firstname_list_users_2.4"
      val lastnameD1 = "lastname_list_users_2.4"

      val usernameD2 = "username_list_users_2.5"
      val firstnameD2 = "firstname_list_users_2.5"
      val lastnameD2 = "lastname_list_users_2.5"

      val groupName = "groupname_list_users_2"
      val userRoleName = Elements.USER
      val deviceRoleName = Elements.DEVICE

      // create group
      val group = TestRefUtil.createSimpleGroup(groupName)
      val roleUser = TestRefUtil.createAndGetSimpleRole(userRoleName)
      val roleDevice = TestRefUtil.createAndGetSimpleRole(deviceRoleName)

      // create users
      val u1 = TestRefUtil.addUserToKC(usernameU1, firstnameU1, lastnameU1)
      val u2 = TestRefUtil.addUserToKC(usernameU2, firstnameU2, lastnameU2)
      val u3 = TestRefUtil.addUserToKC(usernameU3, firstnameU3, lastnameU3)
      val d1 = TestRefUtil.addUserToKC(usernameD1, firstnameD1, lastnameD1)
      val d2 = TestRefUtil.addUserToKC(usernameD2, firstnameD2, lastnameD2)

      // assign roles to users
      u1.resource.addRoles(List(roleUser.toRepresentation))
      u2.resource.addRoles(List(roleUser.toRepresentation))
      u3.resource.addRoles(List(roleUser.toRepresentation))
      d1.resource.addRoles(List(roleDevice.toRepresentation))
      d2.resource.addRoles(List(roleDevice.toRepresentation))

      // F(ront)E(nd) user
      val FeU1 = SimpleUser(
        u1.getKeycloakId,
        usernameU1,
        lastnameU1,
        firstnameU1
      )
      val FeU2 = SimpleUser(
        u2.getKeycloakId,
        usernameU2,
        lastnameU2,
        firstnameU2
      )
      val FeU3 = SimpleUser(
        u3.getKeycloakId,
        usernameU3,
        lastnameU3,
        firstnameU3
      )

      // add users to group
      u1.joinGroup(group.id)
      u2.joinGroup(group.id)
      u3.joinGroup(group.id)
      val t0 = System.currentTimeMillis()
      // test
      val groupMembers = group.getMembers map { u =>
        QuickActions.quickSearchId(u.getId).toResourceRepresentation.toSimpleUser
      }
      groupMembers.sortBy(x => x.id) shouldBe List(FeU1, FeU2, FeU3).sortBy(
        x => x.id
      )
      val t1 = System.currentTimeMillis()
      println(t1 - t0 + " ms")
    }

    scenario("on empty group") {
      // vals
      val groupName = "groupname_list_users_1"
      val group = TestRefUtil.createSimpleGroup(groupName)
      // test
      group.getMembers.size shouldBe 0
    }

    scenario("no user in group") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group = TestRefUtil.createSimpleGroup(groupName)
      val device = TestRefUtil.addUserToKC(username, firstname, lastname)
      val roleName = Elements.DEVICE
      // create role
      val role = TestRefUtil.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new java.util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      device.resource.roles().realmLevel().add(roleRepresentationList)
      device.joinGroup(group.id)
      group.getMembers.map(_.toResourceRepresentation).count(_.isDevice) shouldBe 1
      group.getMembers.map(_.toResourceRepresentation).count(_.isUser) shouldBe 0

    }
  }

  feature("get all groups of a user") {
    scenario("return all groups of a user") {
      val user = TestRefUtil.createSimpleUser()
      val group1 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      val group2 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      user.joinGroup(group1.id)
      user.joinGroup(group2.id)
      user.resource.getAllGroups().map(g => g.toGroupFE).sortBy(x => x.name) shouldBe List(
        group1.toGroupFE,
        group2.toGroupFE
      ).sortBy(x => x.name)
    }

    scenario("return all groups of a user, not OWN_DEVICES") {
      val user = TestRefUtil.createSimpleUser()
      val group1 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      val group2 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      val group3 = TestRefUtil.createSimpleGroup(
        Elements.PREFIX_OWN_DEVICES + TestRefUtil.giveMeRandomString()
      )
      user.joinGroup(group1.id)
      user.joinGroup(group2.id)
      user.joinGroup(group3.id)
      user.getGroupsFiltered.map(g => g.toGroupFE).sortBy(x => x.name) shouldBe List(
        group1.toGroupFE,
        group2.toGroupFE
      ).sortBy(x => x.name)
    }
  }

  feature("create group") {
    scenario("create sample group") {
      val u = TestRefUtil.createSimpleUser()
      val gName = TestRefUtil.giveMeRandomString()
      val g = GroupFactory.createGroup(gName)
      u.joinGroup(g)
      val uGroups = u.resource.groups().asScala.toList
      uGroups.size shouldBe 1
      uGroups.head.getName shouldBe gName
    }
  }

  feature("add devices from user into group") {
    scenario("adding a single device") {
      val device = TestRefUtil.createRandomDeviceFromEmptyKeycloak()
      val user = UserFactory.getByUsername(DEFAULT_USERNAME)

      val g = TestRefUtil.createSimpleGroup("abcde")
      user.addDevicesToGroup(List(device), g.representation)
    }

    scenario("adding multiple devices") {
      val device = TestRefUtil.createRandomDeviceFromEmptyKeycloak()
      val user = UserFactory.getByUsername(new DevicesSpec().DEFAULT_USERNAME)
      val addD = AddDevice(TestRefUtil.giveMeRandomUUID, "aDescription", "default_type", Nil)
      val res = DeviceFactory.createDevice(addD, user.resource).toResourceRepresentation
      val g = TestRefUtil.createSimpleGroup("abcde")
      user.addDevicesToGroup(List(device, res), g.representation)
      g.getMembers.size shouldBe 2
    }
  }

  feature("get all devices in a group") {
    scenario("get all") {
      val devicesCreated = deviceCreation()
      val username = "aaa"
      val groupName = Util.getDeviceGroupNameFromUserName(username)
      val group = GroupFactory.getByName(groupName)
      val t0 = System.currentTimeMillis()
      val devicesReturned = group.resource.getDevicesPagination()
      val t1 = System.currentTimeMillis()
      println(s"time: ${t1 - t0}")
      devicesReturned.size shouldBe 4
      devicesReturned.map { d => d.hwDeviceId.toLowerCase }.sorted shouldBe devicesCreated.map { d => d.hwDeviceId.toLowerCase() }.sorted
    }

    scenario("get only 2 - start") {
      deviceCreation()
      val username = "aaa"
      val groupName = Util.getDeviceGroupNameFromUserName(username)
      val group = GroupFactory.getByName(groupName)
      group.resource.getDevicesPagination(0, 2).size shouldBe 2
    }

    scenario("get only 2 - end") {
      deviceCreation()
      val username = "aaa"
      val groupName = Util.getDeviceGroupNameFromUserName(username)
      val group = GroupFactory.getByName(groupName)
      val devicesInGroup = group.resource.getDevicesPagination(1, 2)
      logger.info(devicesInGroup.mkString(", "))

    }

    scenario("get only 2 - correct ones - start") {
      val devicesCreated = deviceCreation()
      val username = "aaa"
      val groupName = Util.getDeviceGroupNameFromUserName(username)
      val group = GroupFactory.getByName(groupName)
      val devices = group.resource.getDevicesPagination(0, 2).map { d => d.hwDeviceId.toLowerCase }.sorted
      logger.info("devicesCreated = " + devicesCreated.map { d => d.hwDeviceId.toLowerCase }.sorted.mkString(", "))
      logger.info("devicesObtained = " + devices.mkString(", "))
      devices shouldBe devicesCreated.map { d => d.hwDeviceId.toLowerCase }.sorted.take(2)
    }

    scenario("get only 2 - correct ones - end") {
      val devicesCreated = deviceCreation()
      val username = "aaa"
      val groupName = Util.getDeviceGroupNameFromUserName(username)
      val group = GroupFactory.getByName(groupName)
      val devices = group.resource.getDevicesPagination(1, 2).map { d => d.hwDeviceId.toLowerCase }.sorted
      logger.info("devicesCreated = " + devicesCreated.map { d => d.hwDeviceId.toLowerCase }.sorted.mkString(", "))
      logger.info("devicesObtained = " + devices.mkString(", ") + "")
      devices shouldBe devicesCreated.map { d => d.hwDeviceId.toLowerCase }.sorted.takeRight(2)
    }
  }

  /**
    * Create 4 devices and the groups
    */
  def deviceCreation(): List[DeviceCreationState] = {
    val userStruct = SimpleUser("", "aaa", "lastname_cd", "firstname_cd")

    val (hwDeviceId1, deviceType, deviceDescription1) = TestRefUtil.generateDeviceAttributes(description = "1a cool description")
    val (hwDeviceId2, _, deviceDescription2) = TestRefUtil.generateDeviceAttributes(description = "2a cool description")
    val (hwDeviceId3, _, deviceDescription3) = TestRefUtil.generateDeviceAttributes(description = "3a cool description")
    val (hwDeviceId4, _, deviceDescription4) = TestRefUtil.generateDeviceAttributes(description = "4a cool description")

    val (userGroupName, apiConfigName, deviceConfName) = TestRefUtil.createGroupsName(userStruct.username, realmName, deviceType)

    val randomGroupName = "random_group"

    val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

    val deviceConfigRepresentation = new GroupRepresentation
    deviceConfigRepresentation.setAttributes(attributeDConf)
    // create groups
    val randomGroupKc = TestRefUtil.createSimpleGroup(randomGroupName)
    val (userGroup, apiConfigGroup, _) = TestRefUtil.createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)

    // create user
    val user = TestRefUtil.addUserToKC(userStruct)
    ApiUtil.resetUserPassword(user.resource, "password", temporary = false)
    // make user join groups
    user.joinGroup(userGroup.id)
    user.joinGroup(apiConfigGroup.id)

    val listGroupsToJoinId = List(randomGroupKc.id)
    // create roles
    TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
    val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
    user.resource.addRoles(List(userRole.toRepresentation))

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
    import scala.concurrent.duration._
    Await.result(user.createMultipleDevices(ourList), 1.minutes)
  }

}
