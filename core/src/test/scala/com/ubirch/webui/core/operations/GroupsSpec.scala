package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.{GroupNotEmpty, GroupNotFound}
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.User
import org.keycloak.admin.client.resource.{GroupResource, RealmResource}
import org.keycloak.representations.idm.RoleRepresentation
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class GroupsSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = getRealm

  override def beforeEach(): Unit = TestUtils.clearKCRealm

  feature("leave group") {
    scenario("user should leave group that he belongs to") {
      // vals
      val username = "username_leave_group_1"
      val firstname = "firstname_leave_group_1"
      val lastname = "lastname_leave_group_1"
      val groupName = "groupname_leave_group_1"

      // test
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      val group = TestUtils.createSimpleGroup(groupName)
      user.joinGroup(group.toRepresentation.getId)
      group.members(0, 1).get(0).getFirstName shouldBe firstname
      Groups.leaveGroup(user.toRepresentation.getId, group.toRepresentation.getId)
      group.members(0, 1).size() shouldBe 0
    }

    scenario("user should not be able to leave he group that he doesn't belong to") {
      // vals
      val username = "username_leave_group_2"
      val firstname = "firstname_leave_group_2"
      val lastname = "lastname_leave_group_2"
      val groupName = "groupname_leave_group_2"
      val groupname2 = "dummy_group"

      // test
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      val group = TestUtils.createSimpleGroup(groupName)
      val dummyGroup = TestUtils.createSimpleGroup(groupname2)
      user.joinGroup(group.toRepresentation.getId)
      group.members(0, 1).get(0).getFirstName shouldBe firstname
      try {
        Groups.leaveGroup(user.toRepresentation.getId, dummyGroup.toRepresentation.getId)
      } catch {
        case e: Exception => println(e.getMessage)
      }
      group.members(0, 1).size() shouldBe 1
    }
  }

  feature("delete group") {
    scenario("delete existing group") {
      // vals
      val groupName = "groupname_delete_1"

      // test
      val group = TestUtils.createSimpleGroup(groupName)
      Groups.deleteGroup(group.toRepresentation.getId)
      realm.groups().count().get("count") shouldBe 0
    }

    scenario("delete non existing group") {
      assertThrows[GroupNotFound](Groups.deleteGroup("random_super_random_id_123455"))
    }

    scenario("delete group with a user inside -> FAIL") {
      // vals
      val username = "username_leave_group_2"
      val firstname = "firstname_leave_group_2"
      val lastname = "lastname_leave_group_2"
      val groupName = "groupname_leave_group_2"
      //test
      val group = TestUtils.createSimpleGroup(groupName)
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      user.joinGroup(group.toRepresentation.getId)
      assertThrows[GroupNotEmpty](Groups.deleteGroup(group.toRepresentation.getId))
    }
  }

  feature("is a group empty") {
    scenario("empty group") {
      // vals
      val groupName = "groupname_empty_1"
      // test
      val group = TestUtils.createSimpleGroup(groupName)
      Groups.isGroupEmpty(group.toRepresentation.getId) shouldBe true
    }

    scenario("non empty group") {
      // vals
      val username = "username_empty_group_2"
      val firstname = "firstname_empty_group_2"
      val lastname = "lastname_empty_group_2"
      val groupName = "groupname_empty_group_2"
      //test
      val group = TestUtils.createSimpleGroup(groupName)
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      user.joinGroup(group.toRepresentation.getId)
      Groups.isGroupEmpty(group.toRepresentation.getId) shouldBe false
    }

    scenario("non existing group") {
      assertThrows[GroupNotFound](Groups.isGroupEmpty("random_super_empty_id_123455"))
    }
  }

  feature("list all users of group") {
    scenario("group contains only one users") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group: GroupResource = TestUtils.createSimpleGroup(groupName)
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      val roleName = "USER"
      // create role
      val role = TestUtils.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      user.roles().realmLevel().add(roleRepresentationList)
      user.joinGroup(group.toRepresentation.getId)
      val userFE = User(user.toRepresentation.getId, username, lastname, firstname)
      Groups.getMembersInGroup[User](group.toRepresentation.getId, roleName, userRepresentationToUser) shouldBe List(userFE)
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
      val userRoleName = "USER"
      val deviceRoleName = "DEVICE"

      // create group
      val group: GroupResource = TestUtils.createSimpleGroup(groupName)
      val roleUser = TestUtils.createAndGetSimpleRole(userRoleName)
      val roleDevice = TestUtils.createAndGetSimpleRole(deviceRoleName)

      // create users
      val u1 = TestUtils.addUserToKC(usernameU1, firstnameU1, lastnameU1)
      val u2 = TestUtils.addUserToKC(usernameU2, firstnameU2, lastnameU2)
      val u3 = TestUtils.addUserToKC(usernameU3, firstnameU3, lastnameU3)
      val d1 = TestUtils.addUserToKC(usernameD1, firstnameD1, lastnameD1)
      val d2 = TestUtils.addUserToKC(usernameD2, firstnameD2, lastnameD2)

      // assign roles to users
      TestUtils.addRoleToUser(u1, roleUser.toRepresentation)
      TestUtils.addRoleToUser(u2, roleUser.toRepresentation)
      TestUtils.addRoleToUser(u3, roleUser.toRepresentation)
      TestUtils.addRoleToUser(d1, roleDevice.toRepresentation)
      TestUtils.addRoleToUser(d2, roleDevice.toRepresentation)

      // F(ront)E(nd) user
      val FeU1 = User(u1.toRepresentation.getId, usernameU1, lastnameU1, firstnameU1)
      val FeU2 = User(u2.toRepresentation.getId, usernameU2, lastnameU2, firstnameU2)
      val FeU3 = User(u3.toRepresentation.getId, usernameU3, lastnameU3, firstnameU3)

      // add users to group
      u1.joinGroup(group.toRepresentation.getId)
      u2.joinGroup(group.toRepresentation.getId)
      u3.joinGroup(group.toRepresentation.getId)
      val t0 = System.currentTimeMillis()
      // test
      Groups.getMembersInGroup[User](group.toRepresentation.getId, userRoleName, userRepresentationToUser).sortBy(x => x.id) shouldBe List(FeU1, FeU2, FeU3).sortBy(x => x.id)
      val t1 = System.currentTimeMillis()
      println(t1 - t0 + " ms")
    }

    scenario("on empty group") {
      // vals
      val groupName = "groupname_list_users_1"
      val group: GroupResource = TestUtils.createSimpleGroup(groupName)
      // test
      Groups.getMembersInGroup[User](group.toRepresentation.getId, "user", userRepresentationToUser) shouldBe List()
    }

    scenario("no user in group") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group: GroupResource = TestUtils.createSimpleGroup(groupName)
      val user = TestUtils.addUserToKC(username, firstname, lastname)
      val roleName = "DEVICE"
      // create role
      val role = TestUtils.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      user.roles().realmLevel().add(roleRepresentationList)
      user.joinGroup(group.toRepresentation.getId)
      Groups.getMembersInGroup[User](group.toRepresentation.getId, "USER", userRepresentationToUser) shouldBe List()

    }
  }

  feature("get all groups of a user") {
    scenario("return all groups of a user") {
      val user = TestUtils.createSimpleUser()
      val group1 = TestUtils.createSimpleGroup(TestUtils.giveMeRandomString())
      val group2 = TestUtils.createSimpleGroup(TestUtils.giveMeRandomString())
      user.joinGroup(group1.toRepresentation.getId)
      user.joinGroup(group2.toRepresentation.getId)
      Groups.getGroupsOfAUser(user.toRepresentation.getId).sortBy(x => x.name) shouldBe List(Groups.getGroupStructById(group1.toRepresentation.getId), Groups.getGroupStructById(group2.toRepresentation.getId)).sortBy(x => x.name)
    }

    scenario("return all groups of a user, not OWN_DEVICES") {
      val user = TestUtils.createSimpleUser()
      val group1 = TestUtils.createSimpleGroup(TestUtils.giveMeRandomString())
      val group2 = TestUtils.createSimpleGroup(TestUtils.giveMeRandomString())
      val group3 = TestUtils.createSimpleGroup(TestUtils.giveMeRandomString() + "_OWN_DEVICES")
      user.joinGroup(group1.toRepresentation.getId)
      user.joinGroup(group2.toRepresentation.getId)
      user.joinGroup(group3.toRepresentation.getId)
      Groups.getGroupsOfAUser(user.toRepresentation.getId).sortBy(x => x.name) shouldBe List(Groups.getGroupStructById(group1.toRepresentation.getId), Groups.getGroupStructById(group2.toRepresentation.getId)).sortBy(x => x.name)
    }
  }

  feature("create group") {
    scenario("create sample group") {
      val u = TestUtils.createSimpleUser()
      val gName = TestUtils.giveMeRandomString()
      val g = Groups.createGroupAddUser(gName, u.toRepresentation.getId)
      val uGroups = u.groups().asScala.toList
      uGroups.size shouldBe 1
      uGroups.head.getName shouldBe gName
    }
  }

  feature("add devices from user into group") {
    scenario("single device") {
      val deviceId = new DevicesSpec().createRandomDevice()
      val user = getKCUserFromUsername(new DevicesSpec().DEFAULT_USERNAME)

      val g = TestUtils.createSimpleGroup("abcde")
      Groups.addDevicesFromUserToGroup(user.toRepresentation.getId, List(deviceId), g.toRepresentation.getId)
    }

    scenario("multiple devices") {
      val deviceId = new DevicesSpec().createRandomDevice()
      val user = getKCUserFromUsername(new DevicesSpec().DEFAULT_USERNAME)
      val g = TestUtils.createSimpleGroup("abcde")
      Groups.addDevicesFromUserToGroup(user.toRepresentation.getId, List(deviceId), g.toRepresentation.getId)
    }
  }
}
