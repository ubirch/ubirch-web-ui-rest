package com.ubirch.webui.models.keycloak.group

import java.util

import com.ubirch.webui.models.keycloak.util.Converter
import org.json4s.jackson.JsonMethods.parse
import org.json4s.DefaultFormats
import scala.collection.JavaConverters._

case class GroupAttributes(attributes: Map[String, util.List[String]]) {
  def getValue(key: String): String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    val json = parse(attributes.head._2.asScala.head)
    (json \ key).extract[String]
  }
  def asScala: Map[String, List[String]] = Converter.attributesToMap(attributes.asJava)
}
