package com.ubirch.webui.server.Models

import com.ubirch.webui.core.structure.{Device, Group, User}
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import org.scalatra.swagger.ResponseMessage

object SwaggerResponse {
  implicit val formats: DefaultFormats.type = DefaultFormats

  val userExample = User(
    id = "a74bb60a-4560-d079-bf00-68b1675a22c7",
    username = "chrisK",
    lastname = "Krutzler",
    firstname = "Christian"
  )

  val groupExample = Group(
    id = "132456928-efge-43563",
    name = "thermal_sensors_groups"
  )

  val attributesExample: Map[String, List[String]] = Map(
    "size" -> List("5cm"),
    "power source" -> List("battery")
  )

  val deviceExample = Device(
    id = "a74bb60a-d079-4560-bf00-5a22c768b167",
    hwDeviceId = "abef295c-02a7-4eb2-a4b0-dd10441ae5f3",
    description = "Temperature sensor of the living room",
    owner = userExample,
    groups = List(groupExample),
    attributes = attributesExample,
    deviceType = "temperature_sensor",
    created = "1569396364"
  )

  val UNAUTHORIZED = ResponseMessage(Elements.NOT_AUTHORIZED_CODE, "Authorization failed")
  val AUTHORIZED = ResponseMessage(Elements.OK_CODE, "KeyCloakToken")
  val jsonTest: String = write(deviceExample)
  val DEVICE = ResponseMessage(Elements.OK_CODE, jsonTest)
}

