package com.ubirch.webui.server.rest

import com.ubirch.webui.core.structure.member.UserFactory
import com.ubirch.webui.core.structure.util.{ InitKeycloakResponse, Util }
import com.ubirch.webui.{ PopulateRealm, TestBase }
import org.json4s.native.Serialization
import org.json4s.{ Formats, NoTypeHints }
import org.keycloak.admin.client.resource.RealmResource

class ApiUsersSpec extends TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiUsers(), "/*")

  var realmPopulation: InitKeycloakResponse = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    restoreTestEnv()
  }

  implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints)

  def restoreTestEnv(): Unit = {
    implicit val realm: RealmResource = Util.getRealm
    TestRefUtil.clearKCRealm
    realmPopulation = PopulateRealm.doIt()
  }

  def giveMeADeviceHwDeviceId(): String = {
    val chrisx = UserFactory.getByUsername("chrisx")
    val device = chrisx.getOwnDevices.head
    val hwDeviceId = device.getHwDeviceId
    logger.info("hwDeviceId received = " + hwDeviceId)
    hwDeviceId
  }

}
