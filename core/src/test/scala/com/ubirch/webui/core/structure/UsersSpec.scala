package com.ubirch.webui.core.structure

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.structure.member.UserFactory
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, Matchers}

class UsersSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {

  implicit val realmName: String = TestRefUtil.realmName
  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

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
