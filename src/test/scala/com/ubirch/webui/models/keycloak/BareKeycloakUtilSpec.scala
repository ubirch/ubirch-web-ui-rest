package com.ubirch.webui.models.keycloak

import com.ubirch.webui.{ EmbeddedKeycloakUtil, GroupsWithAttribute, GroupWithAttribute, InitKeycloakBuilder, InitKeycloakResponse, PopulateRealm, TestRefUtil, UserDevices, UsersDevices }
import com.ubirch.webui.TestRefUtil.giveMeRandomUUID
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.Elements
import javax.ws.rs.NotFoundException
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

class BareKeycloakUtilSpec extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  val defaultUser: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
  val defaultDevice: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", true)
  val defaultUserDevice = UserDevices(defaultUser, maybeDevicesShould = Option(List(defaultDevice)))
  val defaultUsers = Option(UsersDevices(List(defaultUserDevice)))
  val defaultApiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
  val defaultDeviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
  val defaultConfGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup)))

  val defaultInitKeycloakBuilder = InitKeycloakBuilder(users = defaultUsers, defaultGroups = defaultConfGroups)

  /*
  A default keycloak env: one user, that has no device
   */
  def initKeycloakBuilderNoDevice = InitKeycloakBuilder(
    users = Option(UsersDevices(List(UserDevices(defaultUser, maybeDevicesShould = None)))),
    defaultGroups = defaultConfGroups
  )

  /**
    * Where
    */
  var realmPopulation: InitKeycloakResponse = _

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

  feature("get all groups") {
    scenario("should return all groups") {
      restoreTestEnv()
      val device = realmPopulation.usersResponse.head.devicesResult.head.is
      val groupsName = device.resource.getAllGroups().map(_.getName)
      groupsName.contains("DEVICE_TYPE_default_type") shouldBe true
      groupsName.contains("OWN_DEVICES_username_default") shouldBe true
      groupsName.contains("test-realm_API_CONFIG_default") shouldBe true
      groupsName.size shouldBe 3
    }

    scenario("should return all groups when given None") {
      restoreTestEnv()
      val device = realmPopulation.usersResponse.head.devicesResult.head.is
      val groupsName = device.resource.getAllGroups(None).map(_.getName)
      groupsName.contains("DEVICE_TYPE_default_type") shouldBe true
      groupsName.contains("OWN_DEVICES_username_default") shouldBe true
      groupsName.contains("test-realm_API_CONFIG_default") shouldBe true
      groupsName.size shouldBe 3
    }
  }

  feature("can be deleted") {

    scenario("should be true") {
      restoreTestEnv()
      val device = realmPopulation.usersResponse.head.devicesResult.head.is
      device.resource.canBeDeleted() shouldBe true
    }

    scenario("should be false") {
      restoreTestEnv()
      val device = realmPopulation.usersResponse.head.devicesResult.head.is
      val groupToAdd = new GroupRepresentation()
      groupToAdd.setName("grw" + Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX + "jfbe")
      val res = realm.groups().add(groupToAdd)
      val groupId = Util.getCreatedId(res)
      device.resource.joinGroup(groupId)
      device.resource.canBeDeleted() shouldBe false
    }

  }

  def restoreTestEnv(keycloakBuilder: InitKeycloakBuilder = defaultInitKeycloakBuilder): Unit = {
    implicit val realm: RealmResource = Util.getRealm
    clearRealm
    realmPopulation = PopulateRealm.doIt(keycloakBuilder)
  }

  def clearRealm = TestRefUtil.clearKCRealm
}
