package com.ubirch.webui.services

import com.ubirch.webui.{ InitKeycloakResponse, PopulateRealm, TestBase, TestRefUtil }
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.UserAccountInfo
import org.json4s.native.Serialization
import org.json4s.{ Formats, NoTypeHints }
import org.json4s.jackson.Serialization.read
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.FeatureSpec

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
      get("/accountInfo", Map.empty, Map("Authorization" -> s"bearer ${generateTokenUser()}")) {
        status shouldBe 200
        val bodyParsed = read[UserAccountInfo](body)
        bodyParsed.user.username shouldBe "chrisx"
        bodyParsed.user.lastname shouldBe "elsner"
        bodyParsed.user.firstname shouldBe "christian"
        bodyParsed.user.id != "" shouldBe true
        bodyParsed.numberOfDevices shouldBe 5
        bodyParsed.isAdmin shouldBe true
      }
      get("/accountInfo", Map.empty, Map("Authorization" -> s"bearer ${generateTokenUser("diebeate")}")) {
        status shouldBe 200
        val bodyParsed = read[UserAccountInfo](body)
        bodyParsed.user.username shouldBe "diebeate"
        bodyParsed.user.lastname shouldBe "fiss"
        bodyParsed.user.firstname shouldBe "beate"
        bodyParsed.user.id != "" shouldBe true
        bodyParsed.numberOfDevices shouldBe 1
        bodyParsed.isAdmin shouldBe false
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
    val device = chrisx.getOwnDeviceGroup().getMembers.map(_.toResourceRepresentation).filter(_.isDevice).head
    val hwDeviceId = device.representation.getUsername
    logger.info("hwDeviceId received = " + hwDeviceId)
    hwDeviceId
  }

}
