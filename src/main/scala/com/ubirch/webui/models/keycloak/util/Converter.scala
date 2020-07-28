package com.ubirch.webui.models.keycloak.util

import scala.collection.JavaConverters._

object Converter {

  def attributesToMap(attributes: java.util.Map[String, java.util.List[String]]): Map[String, List[String]] = {
    attributes.asScala.toMap map { keyValue =>
      keyValue._1 -> keyValue._2.asScala.toList
    }
  }
}
