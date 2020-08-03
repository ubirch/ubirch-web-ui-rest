package com.ubirch.webui.models.keycloak

import com.ubirch.webui.{ EmbeddedKeycloakUtil, GroupsWithAttribute, GroupWithAttribute, InitKeycloakBuilder, TestRefUtil, UserDevices, UsersDevices }
import com.ubirch.webui.TestRefUtil.giveMeRandomUUID
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

class BareKeycloakUtilSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  val defaultUser: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
  val defaultDevice: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", true)
  val defaultUserDevice = UserDevices(defaultUser, maybeDevicesShould = Option(List(defaultDevice)))
  val defaultUsers = Option(UsersDevices(List(defaultUserDevice)))
  val defaultApiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
  val defaultDeviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
  val defaultConfGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup)))

  def defaultInitKeycloakBuilder = InitKeycloakBuilder(users = defaultUsers, defaultGroups = defaultConfGroups)

  /*
  A default keycloak env: one user, that has no device
   */
  def initKeycloakBuilderNoDevice = InitKeycloakBuilder(
    users = Option(UsersDevices(List(UserDevices(defaultUser, maybeDevicesShould = None)))),
    defaultGroups = defaultConfGroups
  )

  implicit val realm: RealmResource = Util.getRealm

  override def beforeEach(): Unit = TestRefUtil.clearKCRealm

  feature("add roles") {
    scenario("add one role") {
      val user = TestRefUtil.createSimpleUser()
      val rolesToAdd = TestRefUtil.createRoles(List("coucou")).map(_.toRepresentation)
      user.resource.addRoles(rolesToAdd)
      user.resource.getRoles.map(_.getId).sorted shouldBe rolesToAdd.map(_.getId).sorted
    }

    scenario("add multiple roles") {
      val user = TestRefUtil.createSimpleUser()
      val rolesToAdd = TestRefUtil.createRoles(List("coucou", "salut")).map(_.toRepresentation)
      user.resource.addRoles(rolesToAdd)
      user.resource.getRoles.map(_.getId).sorted shouldBe rolesToAdd.map(_.getId).sorted
    }

    scenario("add already existing role") {
      val user = TestRefUtil.createSimpleUser()
      val rolesToAdd = TestRefUtil.createRoles(List("salut")).map(_.toRepresentation)
      user.resource.addRoles(rolesToAdd)
      user.resource.getRoles.map(_.getId).sorted shouldBe rolesToAdd.map(_.getId).sorted
      user.resource.addRoles(rolesToAdd)
      user.resource.getRoles.size shouldBe 1
    }

    scenario("add already existing role and a new one") {
      val user = TestRefUtil.createSimpleUser()
      val rolesToAdd = TestRefUtil.createRoles(List("salut")).map(_.toRepresentation)
      user.resource.addRoles(rolesToAdd)
      user.resource.getRoles.map(_.getId).sorted shouldBe rolesToAdd.map(_.getId).sorted
      val rolesToAddNew = rolesToAdd ++ TestRefUtil.createRoles(List("coucou")).map(_.toRepresentation)
      user.resource.addRoles(rolesToAddNew)
      user.resource.getRoles.map(_.getId).sorted shouldBe rolesToAddNew.map(_.getId).sorted
    }

    scenario("add role that does no longer exist") {
      val user = TestRefUtil.createSimpleUser()
      val rolesToAdd = TestRefUtil.createRoles(List("salut")).map(_.toRepresentation)
      realm.roles().deleteRole("salut")
      Thread.sleep(100)
      assertThrows[NotFoundException](user.resource.addRoles(rolesToAdd))
      user.resource.getRoles.size shouldBe 0
    }
  }

}
