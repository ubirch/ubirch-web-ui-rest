package com.ubirch.webui

import java.util

import com.ubirch.webui.core.structure.{ DeviceStub, SimpleUser }
import com.ubirch.webui.core.structure.util._

import scala.collection.JavaConverters._

object PopulateRealm extends TestBase {

  implicit val realm = Util.getRealm

  val defaultApiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
  val defaultDeviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
  val thermalSensorGroupAttributes: util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List("""{"type": "thermal_sensor"}""").asJava).asJava
  val thermalSensorGroup = GroupWithAttribute(Util.getDeviceConfigGroupName("thermal_sensor"), thermalSensorGroupAttributes)
  val lightSensorGroupAttributes: util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List("""{"type": "light_sensor"}""").asJava).asJava
  val lightSensorGroup = GroupWithAttribute(Util.getDeviceConfigGroupName("light_sensor"), lightSensorGroupAttributes)
  val elevatorFailSensorGroupAttributes: util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List("""{"type": "elevator_sensor"}""").asJava).asJava
  val elevatorFailSensorGroup = GroupWithAttribute(Util.getDeviceConfigGroupName("elevator_fail_detection"), elevatorFailSensorGroupAttributes)
  val defaultConfGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup, thermalSensorGroup, lightSensorGroup, elevatorFailSensorGroup)))

  val chrisX = SimpleUser("", "chrisx", "elsner", "christian")
  val chrisDevices: List[DeviceStub] = List(
    DeviceStub("42956ef1-307e-49c8-995c-9b5b757828cd", "thermal sensor number 1", "thermal_sensor"),
    DeviceStub("a377cce4-6745-4ea9-893a-64ac6c3135c2", "thermal_sensor_2", "thermal_sensor"),
    DeviceStub("3b3da0c2-e97e-4832-9bcb-29e886aeb5a6", "light sensor", "light_sensor"),
    DeviceStub("b04a29b5-2973-41d9-aeae-882ad2db0220", "testDevice", "light_sensor"),
    DeviceStub("5bea401d-06aa-4146-86f3-73a12f748276", "FTWKBuildingTestSensor", "elevator_fail_detection")
  )
  val chrisAndHisDevices = UserDevices(chrisX, Some(chrisDevices))

  val elCarlos = SimpleUser("", "elcarlos", "sanchez", "carlos")
  val carlosAndHisDevices = UserDevices(elCarlos, None)

  val dieBeate = SimpleUser("", "diebeate", "fiss", "beate")
  val dieBeateDevices: List[DeviceStub] = List(DeviceStub("b3cf114d-0b3e-4d75-8abd-5b8ba69bc12e", "test sensor", "default_type"))
  val beateAndHerDevices = UserDevices(dieBeate, Some(dieBeateDevices))

  val ourUsers = Some(UsersDevices(List(chrisAndHisDevices, carlosAndHisDevices, beateAndHerDevices)))

  def defaultInitKeycloakBuilder = InitKeycloakBuilder(users = ourUsers, defaultGroups = defaultConfGroups)

  /**
    * Populate a keycloak realm with pre-determined user and devices:
    * either the one passed in the keycloakBuilder
    * Or use the default ones:
    * 3 Users: chrisx, elCarlos and dieBeate
    * chrisx has 5 devices, elCarlos none and dieBeate 1
    * Devices have a fixed UUID, description and type
    * Chrisx: [
    *   {"42956ef1-307e-49c8-995c-9b5b757828cd", "thermal sensor number 1", "thermal_sensor"},
    *   {"a377cce4-6745-4ea9-893a-64ac6c3135c2", "thermal_sensor_2", "thermal_sensor"},
    *   {"3b3da0c2-e97e-4832-9bcb-29e886aeb5a6", "light sensor", "light_sensor"},
    *   {"b04a29b5-2973-41d9-aeae-882ad2db0220", "testDevice", "light_sensor"},
    *   {"5bea401d-06aa-4146-86f3-73a12f748276", "FTWKBuildingTestSensor", "elevator_fail_detection"}
    * ],
    * dieBeate: [
    *   {"b3cf114d-0b3e-4d75-8abd-5b8ba69bc12e", "test sensor", "default_type"}
    * ]
    */
  def doIt(keycloakBuilder: InitKeycloakBuilder = defaultInitKeycloakBuilder): InitKeycloakResponse = {
    TestRefUtil.initKeycloakDeviceUser(keycloakBuilder)
  }

}
