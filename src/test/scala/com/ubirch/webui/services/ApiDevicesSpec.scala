package com.ubirch.webui.services

import java.util.Base64

import com.ubirch.webui.{InitKeycloakResponse, PopulateRealm, TestBase, TestRefUtil}
import com.ubirch.webui.models.graph.LastHash
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.util.Util
import org.json4s.{NoTypeHints, _}
import org.json4s.native.Serialization.{read, write}
import org.json4s.native.Serialization
import org.keycloak.admin.client.resource.RealmResource
import org.scalatest.FeatureSpec

class ApiDevicesSpec extends FeatureSpec with TestBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  addServlet(new ApiDevices(new GraphClientMockOk), "/*")

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
        logger.info(body)
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
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.models.Exceptions$BadRequestException",    "message":"page size should not be negative"  }}"""
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
      get(testDevice.representation.getUsername, Map.empty, Map("Authorization" -> s"bearer $token")) {
        val resDeviceFe = read[DeviceFE](body)
        compareDeviceFE(resDeviceFe, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")))
        status shouldBe 200
      }
    }

    scenario("get device that doesn't belong to user -> FAIL") {
      val token: String = generateTokenUser("diebeate")
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val user = UserFactory.getByUsername("dieBeate")
      get(testDevice.representation.getUsername, Map.empty, Map("Authorization" -> s"bearer $token")) {
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.models.Exceptions$PermissionException",    "message":"Device {\"hwDeviceId\":\"42956ef1-307e-49c8-995c-9b5b757828cd\",\"description\":\"thermal sensor number 1\",\"deviceType\":\"thermal_sensor\"} does not belong to user {\"id\":\"USERID\",\"username\":\"diebeate\",\"lastname\":\"fiss\",\"firstname\":\"beate\"}"  }}""".replaceAll("USERID", user.representation.getId)
        status shouldBe 400
      }
    }

    scenario("get one device that doesn't exist -> FAIL") {
      val token: String = generateTokenUser()
      get("1234", Map.empty, Map("Authorization" -> s"bearer $token")) {
        body.filter(_ >= ' ') shouldBe s"""{  "error":{    "error_type":"Bad hwDeviceId",    "message":"provided hwDeviceId: 1234 is not a valid UUID"  }}"""
        status shouldBe 400
      }
    }
  }

  // ----------- get(/search/:search) search for a device by its hwDeviceId or username -----------
  feature("get(/search/:search)") {

    scenario("search for a device that belongs to a user by hwDeviceId -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      get(s"/search/${testDevice.representation.getUsername}", Map.empty, Map("Authorization" -> s"bearer $token")) {
        logger.info("body: " + body)
        val resDeviceFe = read[List[DeviceFE]](body)
        resDeviceFe.length shouldBe 1
        compareDeviceFE(resDeviceFe.head, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")))
        status shouldBe 200
      }
    }

    scenario("search for a device that belongs to a user by description -> SUCCESS") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
      val urlEncodedRequest = testDevice.representation.getLastName.replaceAll(" ", "%20") // hacky URL encode
      get("/search/" + urlEncodedRequest, Map.empty, Map("Authorization" -> s"bearer $token")) {
        val resDeviceFe = read[List[DeviceFE]](body)
        resDeviceFe.length shouldBe 1
        compareDeviceFE(resDeviceFe.head, testDevice.toAddDevice, realmPopulation.getUser("chrisx").get.userResult.should, Map("attributesApiGroup" -> List("""{"password":"password"}"""), "attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""")))
        status shouldBe 200
      }
    }

    scenario("search for a device that doesn't belong to a user by HwDeviceId -> SUCCESS, empty result") {
      val token: String = generateTokenUser("diebeate")
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs
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
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.models.Exceptions$MemberNotFound",    "message":"No member named 42956ef1-307e-49c8-995c-9b5b757828cd in the realm test-realm"  }}"""
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
        body.filter(_ >= ' ') shouldBe """{  "error":{    "error_type":"class com.ubirch.webui.models.Exceptions$BadOwner",    "message":"device does not belong to user"  }}"""
      }
      get("/" + testDevice.hwDeviceId, Map.empty, Map("Authorization" -> s"bearer $tokenOwner")) {
        status shouldBe 200
      }
      restoreTestEnv()
    }

    scenario("delete a device that doesn't exist -> FAIL") {
      val token: String = generateTokenUser()
      val uuid = giveMeRandomUUID
      delete("/" + uuid, Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
        logger.info("body: " + body.filter(_ >= ' '))
        body.filter(_ >= ' ') shouldBe s"""{  "error":{    "error_type":"class com.ubirch.webui.models.Exceptions$$MemberNotFound",    "message":"No member named $uuid in the realm test-realm"  }}"""
      }
      get("/" + "12345", Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 400
      }
      restoreTestEnv()
    }
  }

  feature("update") {
    scenario("test update") {
      val token: String = generateTokenUser()
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs.toDeviceFE

      val t = write(testDevice)
      put(
        uri = "/" + testDevice.hwDeviceId,
        t.getBytes,
        headers = Map("Authorization" -> s"bearer $token")
      ) {
          status shouldBe 200
          println(body)
        }
    }

    scenario("update a device -> wrong user can not update device") {
      implicit val json4sFormats = Serialization.formats(NoTypeHints)
      val userTryingToUpdateIt = "diebeate"
      val token: String = generateTokenUser(userTryingToUpdateIt)
      val testDevice = realmPopulation.getUser("chrisx").get.getFirstDeviceIs.toDeviceFE

      val t = write(testDevice)
      put(
        uri = "/" + testDevice.hwDeviceId,
        t.getBytes,
        headers = Map("Authorization" -> s"bearer $token")
      ) {
          status shouldBe 400
          println(body)
          body.contains(s"device with hwDeviceId ${testDevice.hwDeviceId} does not belong to user $userTryingToUpdateIt") shouldBe true
        }
    }

  }

  feature("adding device UUID") {
    scenario("adding 1 device") {
      val token: String = generateTokenUser()

      val newDevice = AddDevice(
        hwDeviceId = giveMeRandomUUID,
        description = "coucou",
        deviceType = "default_type"
      )

      val req = BulkRequest("creation", List("tag1", "tag2"), None, List(newDevice))

      val t = write(req)
      post(
        uri = "/elephants",
        t.getBytes,
        headers = Map("Authorization" -> s"bearer $token")
      ) {
          status shouldBe 200
          body shouldBe s"""[{"${newDevice.hwDeviceId}":{"state":"ok"}}]"""
        }
    }
  }

  feature("upps") {
    scenario("get last hash") {
      val token: String = generateTokenUser()
      val hwDeviceId = giveMeADeviceHwDeviceId()
      get(s"/lastHash/$hwDeviceId", Map.empty, Map("Authorization" -> s"bearer $token")) {
        status shouldBe 200
        println(body)
      }
    }
  }

  feature("security") {
    scenario("UP-1702 access token for device can not be used to add new device to account") {

      val deviceId = giveMeADeviceHwDeviceId()
      val deviceToken = Auth.auth(deviceId, Base64.getEncoder.encodeToString("password".getBytes()))

      logger.info(s"deviceToken = $deviceToken")
      post("/elephants", Map.empty, Map("Authorization" -> s"bearer $deviceToken")) {
        status shouldBe 401
        body shouldBe "logged in as a device when only a user can be logged as"
      }
    }

    scenario("UP-1702 access token for device can not be used to update a device") {

      val deviceId = giveMeADeviceHwDeviceId()
      val deviceToken = Auth.auth(deviceId, Base64.getEncoder.encodeToString("password".getBytes()))

      logger.info(s"deviceToken = $deviceToken")
      put(s"/$deviceId", Map.empty, Map("Authorization" -> s"bearer $deviceToken")) {
        status shouldBe 401
        body shouldBe "logged in as a device when only a user can be logged as"
      }
    }
  }

  def restoreTestEnv(): Unit = {
    implicit val realm: RealmResource = Util.getRealm
    TestRefUtil.clearKCRealm
    realmPopulation = PopulateRealm.doIt()
  }

  def compareDeviceFE(deviceIs: DeviceFE, deviceShouldBe: AddDevice, ownerShouldBe: SimpleUser, attributesShouldBe: Map[String, List[String]]): Unit = {
    deviceIs.hwDeviceId shouldBe deviceShouldBe.hwDeviceId
    deviceIs.description shouldBe deviceShouldBe.description
    deviceIs.deviceType shouldBe deviceShouldBe.deviceType
    deviceIs.owner.map { u => u.username }.sorted shouldBe List(ownerShouldBe.username)
    deviceIs.owner.map { u => u.lastname }.sorted shouldBe List(ownerShouldBe.lastname)
    deviceIs.owner.map { u => u.firstname }.sorted shouldBe List(ownerShouldBe.firstname)
    deviceIs.attributes shouldBe attributesShouldBe
  }

  def giveMeADeviceHwDeviceId(): String = {
    val chrisx = UserFactory.getByUsername("chrisx")
    val device = chrisx.getOwnDeviceGroup().getMembers.map(_.toResourceRepresentation).filter(_.isDevice).head
    val hwDeviceId = device.getHwDeviceId
    logger.info("hwDeviceId received = " + hwDeviceId)
    hwDeviceId
  }

  def giveMeRandomUUID: String = java.util.UUID.randomUUID().toString

}
