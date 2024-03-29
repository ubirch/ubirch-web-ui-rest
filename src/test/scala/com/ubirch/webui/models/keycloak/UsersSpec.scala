package com.ubirch.webui.models.keycloak

import com.ubirch.webui.{ KeycloakTestContainerUtil, TestRefUtil }
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.util.{ QuickActions, Util }
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.{ RoleRepresentation, UserRepresentation }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }

class UsersSpec extends FeatureSpec with KeycloakTestContainerUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  //override def afterAll(): Unit = stopEmbeddedKeycloak()

  feature("get user") {
    scenario("get user by username") {
      val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestRefUtil.addUserToKCExplodedView(
        userStruct.username,
        userStruct.firstname,
        userStruct.lastname
      )
      val userStructBis = SimpleUser(
        userKc.representation.getId,
        userStruct.username,
        userStruct.lastname,
        userStruct.firstname
      )
      val userFeGottenBack = UserFactory.getByUsername(userKc.representation.getUsername)
      userStructBis shouldBe userFeGottenBack.toSimpleUser
    }

    scenario("get user by id") {
      val userStruct = SimpleUser("", "username_cd", "lastname_cd", "firstname_cd")
      val userKc = TestRefUtil.addUserToKCExplodedView(
        userStruct.username,
        userStruct.firstname,
        userStruct.lastname
      )
      val userStructBis = SimpleUser(
        userKc.representation.getId,
        userStruct.username,
        userStruct.lastname,
        userStruct.firstname
      )
      val userFeGottenBack =
        QuickActions.quickSearchId(userKc.representation.getId)
      userStructBis shouldBe userFeGottenBack.toResourceRepresentation.toSimpleUser
    }

    scenario("no such user") {
      val username = "a70d58a8-e0cd-4693-9016-716ea283c5e6"
      assertThrows[Exception](UserFactory.getByUsername(username))
    }
  }

  feature("fully create user") {
    scenario("createDeviceGroupIfNotExisting doesn't duplicate own device group when used parallely") {
      // create user in kc
      val userRepresentation = new UserRepresentation
      userRepresentation.setEnabled(true)
      userRepresentation.setUsername("coucou")
      val realm = Util.getRealm
      val userKcId = ApiUtil.getCreatedId(realm.users().create(userRepresentation))
      // create role
      val role = new RoleRepresentation
      role.setName(Elements.USER)
      realm.roles().create(role)

      val user = QuickActions.quickSearchId(userKcId).toResourceRepresentation

      // call fullyCreateUser multiple times in //
      val processOfFutures = scala.collection.mutable.ListBuffer.empty[Future[Unit]]
      import scala.concurrent.ExecutionContext.Implicits.global
      for (_ <- 0 to 20) {
        processOfFutures += Future(user.fullyCreate()).map(_ => ())
      }

      val futureProcesses: Future[ListBuffer[Unit]] = Future.sequence(processOfFutures)
      futureProcesses.onComplete {
        case Success(success) =>
          success
        case Failure(error) =>
          throw error
      }
      import scala.concurrent.duration._
      Await.result(futureProcesses, 3.second).toList

      // check that the gorup was only created once. Will return error if it finds more than one
      GroupFactory.getByName(Util.getDeviceGroupNameFromUserName("coucou"))
    }
  }
}
