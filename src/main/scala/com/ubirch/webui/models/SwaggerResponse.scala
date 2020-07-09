package com.ubirch.webui.models

import com.ubirch.webui.models.keycloak.AddDevice
import org.json4s.DefaultFormats
import org.scalatra.swagger.ResponseMessage

object SwaggerResponse {
  implicit val formats: DefaultFormats.type = DefaultFormats

  val UNAUTHORIZED = ResponseMessage(Elements.NOT_AUTHORIZED_CODE, "Authorization failed")
  val AUTHORIZED = ResponseMessage(Elements.OK_CODE, "KeyCloakToken")
}

object SwaggerDefaultValues {
  val BEARER_TOKEN = "bearer es256JwtUbirchToken"
  val OWNER_ID = "4ac68362-8b34-499b-9b4c-4597c744ca7f"
  val HW_DEVICE_ID = "18bf1119-4fc4-428a-b0c1-9dbc5b36b8fd"
  val DESCRIPTION = "A simple device"
  val DEVICE_TYPE = "default_type"
  val IMSI = "310150123456789"
  val X_UBIRCH_CREDENTIAL = "aHR0cHM6Ly9mci53aWtpcGVkaWEub3JnL3dpa2kvRmlzdHVsZV9hbmFsZV9kZV9Mb3Vpc19YSVYjJUMyJUFCX0xhX0dyYW5kZV9vcCVDMyVBOXJhdGlvbl8lQzIlQkI="
  val GROUP_NAME = "My_Special_Devices"
  val GROUP_ID = "939013"
  val USERNAME = "JohnSmith"
  val REALM_NAME = "realm"
  val ADD_DEVICE_LIST = List(AddDevice(
    hwDeviceId = HW_DEVICE_ID,
    description = DESCRIPTION,
    deviceType = DEVICE_TYPE,
    listGroups = List(GROUP_NAME, "Devices_To_Watch"),
    attributes = Map("assigned rooms" -> List("living room", "sleeping room"))
  ))
  val UPDATE_DEVICE = UpdateDevice(
    hwDeviceId = HW_DEVICE_ID,
    ownerId = OWNER_ID,
    apiConfig = Map("conf" -> List("configuration")),
    deviceConfig = Map("conf" -> List("configuration")),
    description = "new description",
    deviceType = "new device type",
    groupList = List("newGroup")
  )
}
