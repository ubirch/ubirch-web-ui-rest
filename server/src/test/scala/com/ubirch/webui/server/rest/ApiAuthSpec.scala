package com.ubirch.webui.server.rest

import java.util.Base64

import com.ubirch.webui.core.structure.TokenProcessor
import com.ubirch.webui.core.structure.member.UserFactory
import com.ubirch.webui.TestBase

class ApiAuthSpec extends TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiAuth(), "/*")

  override def beforeAll(): Unit = {
    super.beforeAll()
    PopulateTestEnv.main(Array.empty)
  }

  scenario("correct authentication") {
    val passwordB64 = Base64.getEncoder.encodeToString(PopulateTestEnv.DEFAULT_PASSWORD)
    get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
      status should equal(200)
    }
  }

  scenario("correct authentication, token should be valid") {
    val passwordB64 = Base64.getEncoder.encodeToString(PopulateTestEnv.DEFAULT_PASSWORD)
    get("/", Map.empty, Map("X-Ubirch-Hardware-Id" -> giveMeADeviceHwDeviceId(), "X-Ubirch-Credential" -> passwordB64)) {
      status should equal(200)
      TokenProcessor.validateToken(body)
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
