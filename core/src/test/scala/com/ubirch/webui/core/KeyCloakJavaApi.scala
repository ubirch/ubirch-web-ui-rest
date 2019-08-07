package com.ubirch.webui.core

import java.util

import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.{GroupRepresentation, RealmRepresentation, UserRepresentation}
import org.scalatest.{FeatureSpec, Matchers}

import scala.Option
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

// I can get all the groups from a realm by specifying the realm name
// I can get all the users from a real (but only some piece of information about them)

class KeyCloakJavaApi extends FeatureSpec with LazyLogging with Matchers{

  val t0 = System.currentTimeMillis()

  val kc = Keycloak.getInstance("http://localhost:8080/auth",
    "master",
    "adim",
    "admin",
    "admin-cli")
  val apiUtil = ApiUtil

  val DEMO_REALM = "demo"


  feature("Use keycloak Java API") {

    scenario("get users from real") {

      val realmName = "demo"
      val listRealms: mutable.Seq[RealmRepresentation] = kc.realms().findAll().asScala

      val real: RealmResource = kc.realm(realmName)

      val users = kc.realm(realmName).users()
      if (users != null) {
        val userList = users.list().asScala
        userList.foreach { u =>
          logger.info(s"user in $realmName: ${u.getUsername}")
        }
      }
      listRealms.foreach{ r =>
        logger.info("realm display name: " + r.getDisplayName)
        r.getFederatedUsers
        val usrList = r.getUsers
        if (usrList != null) {
          logger.info("usrList.size = " + usrList.size().toString)
        }
      }

    }

    scenario("get user information") {
      val response = kc.realm("demo").users().search("user_1234")
      if (response != null ) {
        println("res: " + response.asScala.head.toString)
        println(response.asScala.head.getClientRoles.asScala.mkString(", "))
      } else {
        println("response is null")
      }
    }

    scenario("check groups") {
      val response: GroupRepresentation = kc.realm("demo").groups().groups().asScala.head
      kc.realm("demo").groups().groups()
      if (response != null) {
        response.getId
      }
    }

    scenario("get roles of a user") {
      val realm: RealmResource = kc.realms().realm("demo")
      val roles = realm.users().get("79199444-c6f9-4840-9957-5e88e4dbac66").roles()
      val allRoles = roles.realmLevel().listAll().asScala
      println(allRoles.mkString(", "))
    }

    scenario("create a group") {
      val nameOfNewGroup = "test_group_2"
      val realm = kc.realms().realm(DEMO_REALM)
      val group = new GroupRepresentation()
      group.setName(nameOfNewGroup)
      val groupId = createGroup(realm, group).getId
    }

    scenario("add user / device to an existing group") {
      // variables
      val userName = "new_user"
      val nameOfGroup = "test_group_2"
      val realm = kc.realms().realm(DEMO_REALM)
      //clear existing user
      val res = realm.users().search(userName).asScala.head
      realm.users().delete(res.getId)

      // create (or access if it already exist) a group.
      val group = new GroupRepresentation()

      group.setName(nameOfGroup)
      val ourGroup = realm.groups().groups(nameOfGroup, 0, 2).asScala.head

      // create new user
      val newUser = new UserRepresentation()
      newUser.setUsername(userName)
      val responseAddingNewUserDb = realm.users().create(newUser)
      //    get his id
      val userId = apiUtil.getCreatedId(responseAddingNewUserDb)
      println("UserId: " + userId)

      //    add it to the group
      realm.users().get(userId).joinGroup(ourGroup.getId)

      // verify that user is in group
      val membersInGroup = realm.groups().group(ourGroup.getId).members(0, 10).get(0)
      membersInGroup.getUsername shouldBe userName

      // make user leave group and verify
      realm.users().get(userId).leaveGroup(ourGroup.getId)
      realm.groups().group(ourGroup.getId).members(0, 10).asScala.size shouldBe 0

    }

    scenario("get all groups a user is in") {
      // variables
      val userName = "new_user"
      val nameOfGroup = "test_group_2"
      val realm = kc.realms().realm(DEMO_REALM)

      val userDb = realm.users().search(userName).asScala.head
      val userDbId = userDb.getId

      val groupsWhereUserIs = realm.users().get(userDbId).groups().asScala
      groupsWhereUserIs foreach(ug => println(ug.getName))
    }

    scenario("get user roles") {
      val realm = kc.realms.realm(DEMO_REALM)
      val groupId = realm.groups().groups("test_group", 0, 1).get(0).getId
      val groupDb = realm.groups().group(groupId)
      val lMembers = groupDb.members().asScala
      val userId = "9ec2239d-8f9e-4b16-9db1-c7642a9de988"
      val userDb = realm.users().get(userId)
      val userRolesRealmAll = userDb.roles().realmLevel().listAll()
      val userRolesRealmAvailable = userDb.roles().realmLevel().listAvailable()
      val userRolesRealmEffective = userDb.roles().realmLevel().listEffective()
      println("user role realm level: realm level")
      println("all: " + userRolesRealmAll.asScala.mkString(", "))
      println("available: " + userRolesRealmAvailable.asScala.mkString(", "))
      println("effective: " + userRolesRealmEffective.asScala.mkString(", "))


      val res: List[Try[List[String]]] = lMembers map { m =>
        Try(m.getRealmRoles.asScala.toList)
      } toList
      val b = res map {
        case Success(v) => v
        case Failure(e) => println("fail")
      }
      println(b.mkString(", "))
    }

    scenario("list all members of a group that have the role DEVICE / USER") {
      // variables
      val realm: RealmResource = kc.realms().realm(DEMO_REALM)
      val nameOfGroup = "test_group_get_deivces_user"
      val TYPE_USER = "USER"
      val TYPE_DEVICE = "DEVICE"

      //    user1
      val user = new UserRepresentation
      user.setUsername("abcd")
      val userAttribute = new util.HashMap[String, util.List[String]]
      userAttribute.put("type", List(TYPE_USER).asJava)
      user.setAttributes(userAttribute)

      //    device1
      val device1 = new UserRepresentation
      device1.setUsername("efgh")
      val device1Attribute = new util.HashMap[String, util.List[String]]
      device1Attribute.put("type", List(TYPE_DEVICE).asJava)
      device1.setAttributes(device1Attribute)

      //    device2
      val device2 = new UserRepresentation
      device2.setUsername("ijkl")
      val device2Attribute = new util.HashMap[String, util.List[String]]
      device2Attribute.put("type", List(TYPE_DEVICE).asJava)
      device2Attribute.put("tmtc", List(TYPE_DEVICE).asJava)
      device2.setAttributes(device2Attribute)

      //    device3
      val device3 = new UserRepresentation
      device3.setUsername("mnop")
      val device3Attribute = new util.HashMap[String, util.List[String]]
      device3Attribute.put("type", List(TYPE_DEVICE).asJava)
      device3.setAttributes(device3Attribute)

      ///    group
      val group = new GroupRepresentation
      group.setName(nameOfGroup)

      // create
      //     users
      realm.users().create(user)
      realm.users().create(device1)
      realm.users().create(device2)
      realm.users().create(device3)

      //    group
      realm.groups().add(group)

      // get
      //    group
      val groupDb = realm.groups().groups(group.getName, 0, 1).get(0)
      //    usersId
      val userId = realm.users().search(user.getUsername).asScala.head.getId
      val device1Id = realm.users().search(device1.getUsername).asScala.head.getId
      val device2Id = realm.users().search(device2.getUsername).asScala.head.getId
      val device3Id = realm.users().search(device3.getUsername).asScala.head.getId

      // add to group
      realm.users().get(userId).joinGroup(groupDb.getId)
      realm.users().get(device1Id).joinGroup(groupDb.getId)
      realm.users().get(device2Id).joinGroup(groupDb.getId)
      realm.users().get(device3Id).joinGroup(groupDb.getId)

      // get all users from group
      val listUsersInGroup = realm.groups().group(groupDb.getId).members().asScala

      // display all usernames and attributes
      listUsersInGroup foreach { u =>
        println("user with username: " + u.getUsername + " has attributes: ")
        val uAttributes = u.getAttributes.asScala
        assert(uAttributes.nonEmpty)
        uAttributes foreach { a =>
          println(a)
        }
      }

      // separate users from devices
      def searchType(u: UserRepresentation, typeVal: String): Boolean = {
        if (u.getAttributes.containsKey("type")) {
          u.getAttributes.get("type").contains(typeVal)
        } else false
      }

      val lUsers: mutable.Seq[UserRepresentation] = listUsersInGroup filter { u => searchType(u, TYPE_USER) }

      val lDevices = listUsersInGroup filter { u => searchType(u, TYPE_DEVICE) }

      lUsers foreach { u => println("user  : " + u.getUsername) }
      lDevices foreach { u => println("device: " + u.getUsername) }
      // delete users
      realm.users().get(userId).remove()
      realm.users().get(device1Id).remove()
      realm.users().get(device2Id).remove()
      realm.users().get(device3Id).remove()

      // delete group
      realm.groups().group(groupDb.getId).remove()
      val t1 = System.currentTimeMillis()
      println("time elapsed: " + (t1 - t0).toString + " ms")
    }


  }



  import org.keycloak.admin.client.resource.RealmResource
  import org.keycloak.representations.idm.GroupRepresentation

  private def createGroup(realm: RealmResource, group: GroupRepresentation) = try {
    val response: Response = realm.groups.add(group)
    try {
      val groupId = apiUtil.getCreatedId(response)
      // Set ID to the original rep
      group.setId(groupId)
      group
    } finally if (response != null) response.close()
  }
}
