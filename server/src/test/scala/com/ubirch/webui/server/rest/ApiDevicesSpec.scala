package com.ubirch.webui.server.rest

import com.ubirch.webui.{ PopulateRealm, TestBase }
import com.ubirch.webui.core.structure.{ AddDevice, DeviceFE, SimpleUser }
import com.ubirch.webui.core.structure.member.UserFactory
import com.ubirch.webui.core.structure.util.{ InitKeycloakResponse, Util }
import org.json4s.{ NoTypeHints, _ }
import org.json4s.native.Serialization
import org.json4s.native.Serialization.read
import org.keycloak.admin.client.resource.RealmResource

class ApiDevicesSpec extends TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiDevices(), "/*")

  var realmPopulation: InitKeycloakResponse = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    restoreTestEnv()
  }

  implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints)

  // ----------- get(/page/:page/size/:size) Get devices of one user with pagination -----------
  feature("get(/page/:page/size/:size) ") {
    scenario("get all devices of one user -> SUCCESS") {
      val token: String = generateTokenUser()
      get("/page/0/size/100", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":5,"devices":[{"hwDeviceId":"3b3da0c2-e97e-4832-9bcb-29e886aeb5a6","description":"light sensor","deviceType":"light_sensor"},{"hwDeviceId":"42956ef1-307e-49c8-995c-9b5b757828cd","description":"thermal sensor number 1","deviceType":"thermal_sensor"},{"hwDeviceId":"5bea401d-06aa-4146-86f3-73a12f748276","description":"FTWKBuildingTestSensor","deviceType":"elevator_fail_detection"},{"hwDeviceId":"a377cce4-6745-4ea9-893a-64ac6c3135c2","description":"thermal_sensor_2","deviceType":"thermal_sensor"},{"hwDeviceId":"b04a29b5-2973-41d9-aeae-882ad2db0220","description":"testDevice","deviceType":"light_sensor"}]}"""
        status shouldBe 200
      }
    }

    scenario("get all devices, user has no devices") {
      val token: String = generateTokenUser("elCarlos")
      get("/page/0/size/100", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":0,"devices":[]}"""
        status shouldBe 200
      }
    }

    /*
    Get the first 3 devices of chrisX by alphabetical order, make sure that pagination works
     */
    scenario("get first 3 devices of one user -> SUCCESS") {
      val token: String = generateTokenUser()
      get("/page/0/size/3", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":5,"devices":[{"hwDeviceId":"3b3da0c2-e97e-4832-9bcb-29e886aeb5a6","description":"light sensor","deviceType":"light_sensor"},{"hwDeviceId":"42956ef1-307e-49c8-995c-9b5b757828cd","description":"thermal sensor number 1","deviceType":"thermal_sensor"},{"hwDeviceId":"5bea401d-06aa-4146-86f3-73a12f748276","description":"FTWKBuildingTestSensor","deviceType":"elevator_fail_detection"}]}"""
        status shouldBe 200
      }
    }

    scenario("get last 2 devices of one user -> SUCCESS") {
      val token: String = generateTokenUser()
      get("/page/1/size/3", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":5,"devices":[{"hwDeviceId":"a377cce4-6745-4ea9-893a-64ac6c3135c2","description":"thermal_sensor_2","deviceType":"thermal_sensor"},{"hwDeviceId":"b04a29b5-2973-41d9-aeae-882ad2db0220","description":"testDevice","deviceType":"light_sensor"}]}"""
        status shouldBe 200
      }
    }

    scenario("out of bound should result in a zero list array") {
      val token: String = generateTokenUser()
      get("/page/5/size/100", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":5,"devices":[]}"""
        status shouldBe 200
      }
    }

    scenario("negative page number should return an empty array") {
      val token: String = generateTokenUser()
      get("/page/-1/size/100", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body shouldBe """{"numberOfDevices":5,"devices":[]}"""
        status shouldBe 200
      }
    }

    scenario("negative size should return an error") {
      val token: String = generateTokenUser()
      get("/page/0/size/-1", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$BadRequestException",    "message":"page size should not be negative"  }}"""
        status shouldBe 400
      }
    }

  }

  feature("get(/:id) get a specific device information") {

    /**
      * Get single device from UUID. Sign in as the owner. Result should have the DeviceFE format
      */
    scenario("get one device -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      get(testDevice.getHwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        val resDeviceFe = read[DeviceFE](body)
        compareDeviceFE(resDeviceFe, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")), Util.getCustomerId(realmName))
        status shouldBe 200
      }
    }

    scenario("get device that doesn't belong to user -> FAIL") {
      val token: String = generateTokenUser("diebeate")
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val user = UserFactory.getByUsername("dieBeate")
      get(testDevice.getHwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$PermissionException",    "message":"Device {\"hwDeviceId\":\"42956ef1-307e-49c8-995c-9b5b757828cd\",\"description\":\"thermal sensor number 1\",\"deviceType\":\"thermal_sensor\"} does not belong to user {\"id\":\"USERID\",\"username\":\"diebeate\",\"lastname\":\"fiss\",\"firstname\":\"beate\"}"  }}""".replaceAll("USERID", user.memberId)
        status shouldBe 400
      }
    }

    scenario("get one device that doesn't exist -> FAIL") {
      val token: String = generateTokenUser()
      get("1234", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body.filter(_ >= ' ') shouldBe s"""{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$$MemberNotFound",    "message":"No member named 1234 in the realm test-realm"  }}"""
        status shouldBe 400
      }
    }
  }

  // ----------- get(/search/:search) search for a device by its hwDeviceId or username -----------
  feature("get(/search/:search)") {

    scenario("search for a device that belongs to a user by hwDeviceId -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      get(s"/search/${testDevice.getHwDeviceId}", Map.empty, Map("Authorization" -> s"bearer $token")) {
        logger.info("body: " + body)
        val resDeviceFe = read[List[DeviceFE]](body)
        resDeviceFe.length shouldBe 1
        compareDeviceFE(resDeviceFe.head, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")), Util.getCustomerId(realmName))
        status shouldBe 200
      }
    }

    scenario("search for a device that belongs to a user by description -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val urlEncodedRequest = testDevice.getDescription.replaceAll(" ", "%20") // hacky URL encode
      get("/search/" + urlEncodedRequest, Map.empty, Map("Authorization" -> s"bearer $token")) {
        val resDeviceFe = read[List[DeviceFE]](body)
        resDeviceFe.length shouldBe 1
        compareDeviceFE(resDeviceFe.head, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")), Util.getCustomerId(realmName))
        status shouldBe 200
      }
    }

    scenario("search for a device that doesn't belong to a user by HwDeviceId -> SUCCESS, empty result") {
      val token: String = generateTokenUser("diebeate")
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val user = realmPopulation.getUser("diebeate").get.userResult.should
      get("/search/" + testDevice.getHwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe "[]"
      }
    }

    scenario("search for a device that doesn't belong to a user by description -> SUCCESS, empty result") {
      val token: String = generateTokenUser("diebeate")
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val urlEncodedRequest = testDevice.getDescription.replaceAll(" ", "%20") // hacky URL encode
      get("/search/" + urlEncodedRequest, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe "[]"
      }
    }

    scenario("search empty keyword -> SUCCESS, return all devices belonging to user") {
      val token: String = generateTokenUser()
      get(s"/search/%20", Map.empty, Map("Authorization" -> s"bearer $token")) {
        logger.info("body: " + body.filter(_ >= ' '))
        val resDeviceFe = read[List[DeviceFE]](body)
        resDeviceFe.length shouldBe 5
        resDeviceFe.map { d => d.hwDeviceId }.sorted shouldBe realmPopulation.getUser("chrisx").get.devicesResult.map { d => d.is.getHwDeviceId }.sorted
        status shouldBe 200
      }
    }
  }

  feature("delete(/:hwdeviceid): delete a device that belongs to a user") {
    scenario("delete device that belongs to user -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs.toDeviceFE
      delete("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        body shouldBe ""
      }
      get("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
      }
      restoreTestEnv()
    }

    scenario("delete twice a device -> FAIL") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs.toDeviceFE
      delete("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        body shouldBe ""
      }
      delete("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$MemberNotFound",    "message":"No member named 42956ef1-307e-49c8-995c-9b5b757828cd in the realm test-realm"  }}"""
      }
      get("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
      }
      restoreTestEnv()
    }

    scenario("delete a device that doesn't belong to the user -> FAIL") {
      val token: String = generateTokenUser("diebeate")
      val tokenOwner: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs.toDeviceFE
      delete("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$BadOwner",    "message":"device does not belong to user"  }}"""
      }
      get("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $tokenOwner")) {
        status shouldBe 200
      }
      restoreTestEnv()
    }

    scenario("delete a device that doesn't exist -> FAIL") {
      val token: String = generateTokenUser()
      delete("/" + "12345", Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.core.Exceptions$MemberNotFound",    "message":"No member named 12345 in the realm test-realm"  }}"""
      }
      get("/" + "12345", Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
      }
      restoreTestEnv()
    }
  }

  def restoreTestEnv(): Unit = {
    implicit val realm: RealmResource = Util.getRealm
    TestRefUtil.clearKCRealm
    realmPopulation = PopulateRealm.doIt()
  }

  def compareDeviceFE(deviceIs: DeviceFE, deviceShouldBe: AddDevice, ownerShouldBe: SimpleUser, attributesShouldBe: Map[String, List[String]], customerIdShouldBe: String): Unit = {
    deviceIs.hwDeviceId shouldBe deviceShouldBe.hwDeviceId
    deviceIs.description shouldBe deviceShouldBe.description
    deviceIs.deviceType shouldBe deviceShouldBe.deviceType
    deviceIs.owner.map { u => u.username }.sorted shouldBe List(ownerShouldBe.username)
    deviceIs.owner.map { u => u.lastname }.sorted shouldBe List(ownerShouldBe.lastname)
    deviceIs.owner.map { u => u.firstname }.sorted shouldBe List(ownerShouldBe.firstname)
    deviceIs.attributes shouldBe attributesShouldBe
    deviceIs.customerId shouldBe customerIdShouldBe
  }

}
