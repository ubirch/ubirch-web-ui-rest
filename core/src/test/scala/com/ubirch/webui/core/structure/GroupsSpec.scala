package com.ubirch.webui.core.structure

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.GroupNotEmpty
import com.ubirch.webui.core.structure.group.{Group, GroupFactory}
import com.ubirch.webui.core.structure.member.UserFactory
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.RoleRepresentation
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class GroupsSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  feature("leave group") {
    scenario("user should leave group that he belongs to") {
      // vals
      val username = "username_leave_group_1"
      val firstname = "firstname_leave_group_1"
      val lastname = "lastname_leave_group_1"
      val groupName = "groupname_leave_group_1"

      // test
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      val group = TestRefUtil.createSimpleGroup(groupName)
      user.joinGroup(group)
      group.getMembers.members.head.getFirstName shouldBe firstname
      user.leaveGroup(group)
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
        user.joinGroup(group)
        group.getMembers.members.head.getFirstName shouldBe firstname
        try {
          user.leaveGroup(dummyGroup)
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
      group.deleteGroup
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
      user.joinGroup(group)
      assertThrows[GroupNotEmpty](group.deleteGroup)
    }
  }

  feature("is a group empty") {
    scenario("empty group") {
      // vals
      val groupName = "groupname_empty_1"
      // test
      val group = TestRefUtil.createSimpleGroup(groupName)
      group.isEmpty shouldBe true
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
      user.joinGroup(group)
      group.isEmpty shouldBe false
    }

  }

  feature("list all users of group") {
    scenario("group contains only one users") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group: Group = TestRefUtil.createSimpleGroup(groupName)
      val user = TestRefUtil.addUserToKC(username, firstname, lastname)
      val roleName = Elements.USER
      // create role
      val role = TestRefUtil.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      user.keyCloakMember.roles().realmLevel().add(roleRepresentationList)
      user.joinGroup(group)
      val userFE =
        SimpleUser(user.toRepresentation.getId, username, lastname, firstname)
      group.getMembers.members.head.getUsername shouldBe userFE.username
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
      val group: Group = TestRefUtil.createSimpleGroup(groupName)
      val roleUser = TestRefUtil.createAndGetSimpleRole(userRoleName)
      val roleDevice = TestRefUtil.createAndGetSimpleRole(deviceRoleName)

      // create users
      val u1 = TestRefUtil.addUserToKC(usernameU1, firstnameU1, lastnameU1)
      val u2 = TestRefUtil.addUserToKC(usernameU2, firstnameU2, lastnameU2)
      val u3 = TestRefUtil.addUserToKC(usernameU3, firstnameU3, lastnameU3)
      val d1 = TestRefUtil.addUserToKC(usernameD1, firstnameD1, lastnameD1)
      val d2 = TestRefUtil.addUserToKC(usernameD2, firstnameD2, lastnameD2)

      // assign roles to users
      u1.addRole(roleUser.toRepresentation)
      u2.addRole(roleUser.toRepresentation)
      u3.addRole(roleUser.toRepresentation)
      d1.addRole(roleDevice.toRepresentation)
      d2.addRole(roleDevice.toRepresentation)

      // F(ront)E(nd) user
      val FeU1 = SimpleUser(
        u1.toRepresentation.getId,
        usernameU1,
        lastnameU1,
        firstnameU1
      )
      val FeU2 = SimpleUser(
        u2.toRepresentation.getId,
        usernameU2,
        lastnameU2,
        firstnameU2
      )
      val FeU3 = SimpleUser(
        u3.toRepresentation.getId,
        usernameU3,
        lastnameU3,
        firstnameU3
      )

      // add users to group
      u1.joinGroup(group)
      u2.joinGroup(group)
      u3.joinGroup(group)
      val t0 = System.currentTimeMillis()
      // test
      val groupMembers = group.getMembers.members map { u =>
        UserFactory.getByKeyCloakId(u.memberId).toSimpleUser
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
      val group: Group = TestRefUtil.createSimpleGroup(groupName)
      // test
      group.numberOfMembers shouldBe 0
    }

    scenario("no user in group") {
      // vals
      val username = "username_list_users_1"
      val firstname = "firstname_list_users_1"
      val lastname = "lastname_list_users_1"
      val groupName = "groupname_list_users_1"
      val group: Group = TestRefUtil.createSimpleGroup(groupName)
      val device = TestRefUtil.addUserToKC(username, firstname, lastname)
      val roleName = Elements.DEVICE
      // create role
      val role = TestRefUtil.createAndGetSimpleRole(roleName)
      // get role
      val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
      roleRepresentationList.add(role.toRepresentation)
      // add role to user
      device.keyCloakMember.roles().realmLevel().add(roleRepresentationList)
      device.joinGroup(group)
      group.getMembers.getDevices.size shouldBe 1
      group.getMembers.getUsers.size shouldBe 0

    }
  }

  feature("get all groups of a user") {
    scenario("return all groups of a user") {
      val user = TestRefUtil.createSimpleUser()
      val group1 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      val group2 =
        TestRefUtil.createSimpleGroup(TestRefUtil.giveMeRandomString())
      user.joinGroup(group1)
      user.joinGroup(group2)
      user.getGroups.map(g => g.toGroupFE).sortBy(x => x.name) shouldBe List(
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
      user.joinGroup(group1)
      user.joinGroup(group2)
      user.joinGroup(group3)
      user.getGroups.map(g => g.toGroupFE).sortBy(x => x.name) shouldBe List(
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
      val uGroups = u.keyCloakMember.groups().asScala.toList
      uGroups.size shouldBe 1
      uGroups.head.getName shouldBe gName
    }
  }

  feature("add devices from user into group") {
    scenario("single device") {
      val device = new DevicesSpec().createRandomDevice()
      val user = UserFactory.getByUsername(new DevicesSpec().DEFAULT_USERNAME)

      val g = TestRefUtil.createSimpleGroup("abcde")
      user.addDevicesToGroup(List(device), g)
    }

    scenario("multiple devices") {
      val device = new DevicesSpec().createRandomDevice()
      val user = UserFactory.getByUsername(new DevicesSpec().DEFAULT_USERNAME)
      val addD = AddDevice("abcd", "aDescription", "default_type", Nil)
      val res = user.createNewDevice(addD)
      val g = TestRefUtil.createSimpleGroup("abcde")
      user.addDevicesToGroup(List(device, res), g)
      g.numberOfMembers shouldBe 2
    }
  }
}
