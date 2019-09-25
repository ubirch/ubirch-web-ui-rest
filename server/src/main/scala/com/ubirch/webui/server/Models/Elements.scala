package com.ubirch.webui.server.Models

case class UpdateDevice(
    hwDeviceId: String,
    ownerId: String,
    apiConfig: String,
    deviceConfig: String,
    description: String,
    deviceType: String,
    groupList: List[String]
)

object Elements {
  val NOT_AUTHORIZED_CODE = 401
  val OK_CODE = 200
}
