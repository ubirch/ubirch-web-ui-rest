package com.ubirch.webui.services

import java.util.Base64

import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.TokenProcessor
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui._
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.FeatureSpec

class ApiAuthSpec extends FeatureSpec with TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiAuth(), "/*")

  override def beforeAll(): Unit = {
    super.beforeAll()
    restoreTestEnv()
  }

  var realmPopulation: InitKeycloakResponse = _

  def restoreTestEnv(): Unit = {
    implicit val realm: RealmResource = Util.getRealm
    TestRefUtil.clearKCRealm
    realmPopulation = PopulateRealm.doIt()
  }

  scenario("correct authentication") {
    val passwordB64 = Base64.getEncoder.encodeToString(DEFAULT_PWD)
    get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
      status should equal(200)
    }
  }

  scenario("correct authentication, token should be valid") {
    val passwordB64 = Base64.getEncoder.encodeToString(DEFAULT_PWD)
    get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
      status should equal(200)
      TokenProcessor.validateToken(body).get
    }
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    stopEmbeddedKeycloak()
  }

  def giveMeADeviceHwDeviceId(): String = {
    val chrisx = UserFactory.getByUsername("chrisx")
    val device = chrisx.getOwnDevices.head
    val hwDeviceId = device.getHwDeviceId
    logger.info("hwDeviceId received = " + hwDeviceId)
    hwDeviceId
  }

}
