package com.ubirch.webui.services

import com.ubirch.webui.{ InitKeycloakResponse, PopulateRealm, TestBase, TestRefUtil }
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.UserAccountInfo
import org.json4s.{ Formats, NoTypeHints }
import org.json4s.native.Serialization
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.FeatureSpec
import org.json4s.{ DefaultFormats, Formats, _ }
import org.json4s.jackson.Serialization.{ read, write }

class ApiUsersSpec extends FeatureSpec with TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiUsers(), "/*")

  var realmPopulation: InitKeycloakResponse = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    restoreTestEnv()
  }

  feature("accountInfo") {

    scenario("default test") {
      val token: String = generateTokenUser()
      get("/accountInfo", Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        val bodyParsed = read[UserAccountInfo](body)
        bodyParsed.user.username shouldBe "chrisx"
        bodyParsed.user.lastname shouldBe "elsner"
        bodyParsed.user.firstname shouldBe "christian"
        bodyParsed.user.id != "" shouldBe true
        bodyParsed.numberOfDevices shouldBe 5
        bodyParsed.isAdmin shouldBe false
      }
      get("/accountInfo", Map.empty, Map("Authorization" -> s"bearer $token")) {

      }
    }

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
