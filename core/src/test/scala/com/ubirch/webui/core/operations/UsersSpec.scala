package com.ubirch.webui.core.operations

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.operations.Users._
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.User
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

class UsersSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = getRealm

  override def beforeEach(): Unit = TestUtils.clearKCRealm

  feature("get user") {
    scenario("get user by username") {
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestUtils.addUserToKC(userStruct.username, userStruct.firstname, userStruct.lastname)
      val userStructBis = User(userKc.toRepresentation.getId, userStruct.username, userStruct.lastname, userStruct.firstname)
      val userFeGottenBack = findUserByUsername(userKc.toRepresentation.getUsername)
      userStructBis shouldBe userFeGottenBack
    }

    scenario("get user by id") {
      val userStruct = User("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestUtils.addUserToKC(userStruct.username, userStruct.firstname, userStruct.lastname)
      val userStructBis = User(userKc.toRepresentation.getId, userStruct.username, userStruct.lastname, userStruct.firstname)
      val userFeGottenBack = findUserById(userKc.toRepresentation.getId)
      userStructBis shouldBe userFeGottenBack
    }

    scenario("no such user") {
      val username = "a70d58a8-e0cd-4693-9016-716ea283c5e6"
      assertThrows[Exception](Users.findUserByUsername(username))
    }
  }
}
