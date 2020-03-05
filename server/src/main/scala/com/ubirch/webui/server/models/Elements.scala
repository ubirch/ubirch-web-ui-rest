package com.ubirch.webui.server.models

case class UpdateDevice(
    hwDeviceId: String,
    ownerId: String,
    apiConfig: Map[String, List[String]],
    deviceConfig: Map[String, List[String]],
    description: String,
    deviceType: String,
    groupList: List[String]
)

object Elements {
  val NOT_AUTHORIZED_CODE = 401
  val OK_CODE = 200
  val ERROR_REQUEST_CODE = 400
}
