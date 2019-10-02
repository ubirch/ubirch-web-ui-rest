package com.ubirch.webui.core.structure

object Elements {
  val PREFIX_OWN_DEVICES = "OWN_DEVICES_"
  val PREFIX_API = "_API_CONFIG_"
  val PREFIX_DEVICE_TYPE = "DEVICE_TYPE_"
  val DEVICE = "DEVICE"
  val USER = "USER"
  val DEFAULT_FIRST_NAME = " "
  val ATTRIBUTES_DEVICE_GROUP_NAME = "attributesDeviceGroup"
  val ATTRIBUTES_API_GROUP_NAME = "attributesApiGroup"
}

// *_DeviceConfigGroup => PREFIX_DEVICE_TYPE + *
// username_OWN_DEVICES => OWN_DEVICES_username
// realmName_apiConfigGroup_default => realmName + PREFIX_API +default

/*
  val API_GROUP_PART_NAME = "_apiConfigGroup_default"
  val DEVICE_GROUP_PART_NAME = "_DeviceConfigGroup"
  val USER_DEVICE_PART_NAME = "_OWN_DEVICES"
*/
