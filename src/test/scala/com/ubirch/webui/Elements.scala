package com.ubirch.webui

import scala.collection.JavaConverters._

trait Elements {
  val DEFAULT_DESCRIPTION = "a cool description for a cool device"
  val DEFAULT_REALM_NAME = "test-realm"
  val DEFAULT_TYPE = "default_type"
  val DEFAULT_PWD = "0000000f-e000-0000-000f-0000f000e000"
  val DEFAULT_ATTRIBUTE_D_CONF = "value1"
  val DEFAULT_ATTRIBUTE_API_CONF = "{\"password\":\"" + DEFAULT_PWD + "\"}"
  val DEFAULT_MAP_ATTRIBUTE_D_CONF: java.util.Map[String, java.util.List[String]] = Map(
    "attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF).asJava
  ).asJava
  val DEFAULT_MAP_ATTRIBUTE_API_CONF: java.util.Map[String, java.util.List[String]] = Map(
    "attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF).asJava
  ).asJava
  val DEFAULT_MAP_ATTRIBUTE_D_CONF_SCALA: Map[String, List[String]] = Map(
    "attributesDeviceGroup" -> List(DEFAULT_ATTRIBUTE_D_CONF)
  )
  val DEFAULT_MAP_ATTRIBUTE_API_CONF_SCALA: Map[String, List[String]] = Map(
    "attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF)
  )

  val DEFAULT_USERNAME = "username_default"
  val DEFAULT_LASTNAME = "lastname_default"
  val DEFAULT_FIRSTNAME = "firstname_default"
}
