package com.ubirch.webui.services

import java.util.Base64

import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.TokenProcessor
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
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

  feature("auth") {
    scenario("correct authentication") {
      val passwordB64 = Base64.getEncoder.encodeToString(DEFAULT_PWD)
      get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
        status shouldBe 200
      }
    }

    scenario("correct authentication, token should be valid") {
      val passwordB64 = Base64.getEncoder.encodeToString(DEFAULT_PWD)
      get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
        status shouldBe 200
        TokenProcessor.validateToken(body).get
      }
    }
  }

  feature("device auth") {
    val passwordB64 = Base64.getEncoder.encodeToString(DEFAULT_PWD)
    scenario("auth ok -> should return device fe") {
      val token: String = generateTokenUser(giveMeADeviceHwDeviceId(), DEFAULT_PWD)
      get("/deviceInfo", Map.empty, Map(FeUtils.tokenHeaderName -> s"bearer $token")) {
        status shouldBe 200
      }
    }

    scenario("auth ok -> should return device dumb") {
      val hwDeviceId = giveMeADeviceHwDeviceId()
      val token = generateTokenUser(giveMeADeviceHwDeviceId(), DEFAULT_PWD)
      get("/simpleDeviceInfo", Map.empty, Map(FeUtils.tokenHeaderName -> s"bearer $token")) {
        body.contains(hwDeviceId) shouldBe true
        status shouldBe 200
      }
    }

    scenario("bad token -> error") {
      val token = generateTokenUser(giveMeADeviceHwDeviceId(), DEFAULT_PWD)
      get("/simpleDeviceInfo", Map.empty, Map(FeUtils.tokenHeaderName -> (s"bearer $token" + "a"))) {
        status shouldBe 400
      }
    }

    scenario("token belonging to a user -> error") {
      val token = generateTokenUser()
      get("/simpleDeviceInfo", Map.empty, Map(FeUtils.tokenHeaderName -> s"bearer $token")) {
        body shouldBe "logged in as a user when only a device can be logged as"
        status shouldBe 401
      }
    }
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    stopEmbeddedKeycloak()
  }

  def giveMeADeviceHwDeviceId(): String = {
    val chrisx = UserFactory.getByUsername("chrisx")
    val device = chrisx.getOwnDeviceGroup().getMembers.map(_.toResourceRepresentation).filter(_.isDevice).head
    val hwDeviceId = device.getHwDeviceId
    logger.info("hwDeviceId received = " + hwDeviceId)
    hwDeviceId
  }

}
