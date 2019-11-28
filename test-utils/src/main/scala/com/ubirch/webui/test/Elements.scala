package com.ubirch.webui.test

import java.util

import scala.collection.JavaConverters._

trait Elements {
  val DEFAULT_DESCRIPTION = "a cool description for a cool device"
  val DEFAULT_TYPE = "default_type"
  val DEFAULT_PWD = "password"
  val DEFAULT_ATTRIBUTE_D_CONF = "value1"
  val DEFAULT_ATTRIBUTE_API_CONF = "{\"password\":\"password\"}"
  val DEFAULT_MAP_ATTRIBUTE_D_CONF: util.Map[String, util.List[String]] = Map(
    "attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF).asJava
  ).asJava
  val DEFAULT_MAP_ATTRIBUTE_API_CONF: util.Map[String, util.List[String]] = Map(
    "attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF).asJava
  ).asJava
  val DEFAULT_USERNAME = "username_default"
  val DEFAULT_LASTNAME = "lastname_default"
  val DEFAULT_FIRSTNAME = "firstname_default"
}
