package com.ubirch.webui.services

//import com.ubirch.webui.{ InitKeycloakResponse, PopulateRealm, TestBase, TestRefUtil }
//import com.ubirch.webui.models.keycloak.member.UserFactory
//import com.ubirch.webui.models.keycloak.util.Util
//import org.json4s.{ Formats, NoTypeHints }
//import org.json4s.native.Serialization
//import org.keycloak.admin.client.resource.RealmResource
//import org.scalatest.Ignore
//
//@Ignore
//class ApiUsersSpec extends TestBase {
//
//  implicit val swagger: ApiSwagger = new ApiSwagger
//
//  addServlet(new ApiUsers(), "/*")
//
//  var realmPopulation: InitKeycloakResponse = _
//
//  override def beforeAll(): Unit = {
//    super.beforeAll()
//    restoreTestEnv()
//  }
//
//  implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints)
//
//  def restoreTestEnv(): Unit = {
//    implicit val realm: RealmResource = Util.getRealm
//    TestRefUtil.clearKCRealm
//    realmPopulation = PopulateRealm.doIt()
//  }
//
//  def giveMeADeviceHwDeviceId(): String = {
//    val chrisx = UserFactory.getByUsername("chrisx")
//    val device = chrisx.getOwnDevices.head
//    val hwDeviceId = device.getHwDeviceId
//    logger.info("hwDeviceId received = " + hwDeviceId)
//    hwDeviceId
//  }
//
//}