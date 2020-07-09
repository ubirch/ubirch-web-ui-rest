package com.ubirch.webui.models

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
  val PREFIX_OWN_DEVICES = "OWN_DEVICES_"
  val OWN_DEVICES_GROUP_USERNAME_PLACE = 1
  val PREFIX_API = "_API_CONFIG_"
  val PREFIX_DEVICE_TYPE = "DEVICE_TYPE_"
  val DEVICE_TYPE_TYPE_PLACE = 1
  val DEVICE = "DEVICE"
  val ADMIN = "ADMIN"
  val USER = "USER"
  val DEFAULT_FIRST_NAME = " "
  val ATTRIBUTES_DEVICE_GROUP_NAME = "attributesDeviceGroup"
  val ATTRIBUTES_API_GROUP_NAME = "attributesApiGroup"
  val UNCLAIMED_DEVICES_GROUP_NAME = "UNCLAIMED_DEVICES"
  val PROVIDER_GROUP_PREFIX = "PROVIDER_DEVICES_"
  val FIRST_CLAIMED_TIMESTAMP = "FIRST_CLAIMED_TIMESTAMP"
  val FIRST_CLAIMED_GROUP_NAME_PREFIX = "FIRST_CLAIMED_"
  val CLAIMING_TAGS_NAME = "claiming_tags"
  val CLAIMED = "CLAIMED_"
}
