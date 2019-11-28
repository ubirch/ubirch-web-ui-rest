package com.ubirch.webui.core.structure

import com.ubirch.webui.core.structure.member.UserFactory
import com.ubirch.webui.core.TestRefUtil
import com.ubirch.webui.test.EmbeddedKeycloakUtil
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers}

class UsersSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val realm = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm
  //override def afterAll(): Unit = stopEmbeddedKeycloak()

  feature("get user") {
    scenario("get user by username") {
      val userStruct =
        SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestRefUtil.addUserToKC(
        userStruct.username,
        userStruct.firstname,
        userStruct.lastname
      )
      val userStructBis = SimpleUser(
        userKc.toRepresentation.getId,
        userStruct.username,
        userStruct.lastname,
        userStruct.firstname
      )
      val userFeGottenBack =
        UserFactory.getByUsername(userKc.toRepresentation.getUsername)
      userStructBis shouldBe userFeGottenBack.toSimpleUser
    }

    scenario("get user by id") {
      val userStruct =
        SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestRefUtil.addUserToKC(
        userStruct.username,
        userStruct.firstname,
        userStruct.lastname
      )
      val userStructBis = SimpleUser(
        userKc.toRepresentation.getId,
        userStruct.username,
        userStruct.lastname,
        userStruct.firstname
      )
      val userFeGottenBack =
        UserFactory.getByKeyCloakId(userKc.toRepresentation.getId)
      userStructBis shouldBe userFeGottenBack.toSimpleUser
    }

    scenario("no such user") {
      val username = "a70d58a8-e0cd-4693-9016-716ea283c5e6"
      assertThrows[Exception](UserFactory.getByUsername(username))
    }
  }
}
