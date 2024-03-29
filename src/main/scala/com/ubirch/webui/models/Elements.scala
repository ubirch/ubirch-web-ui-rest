package com.ubirch.webui.models

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
  val ADMIN = "CONSOLE_ADMIN"
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
  val DEFAULT_PASSWORD_GROUP_PREFIX = "DEFAULT_PASSWORD_"
  val DEFAULT_PASSWORD_GROUP_ATTRIBUTE = "DEFAULT_PASSWORD"
  val DEFAULT_PASSWORD_USER_ATTRIBUTE = "DEFAULT_DEVICE_PASSWORD"

  val UBIRCH_SDS_HEADER_ID = "X-Ubirch-Hardware-Id"
  val UBIRCH_SDS_HEADER_PASSWORD = "X-Ubirch-Credential"
}
