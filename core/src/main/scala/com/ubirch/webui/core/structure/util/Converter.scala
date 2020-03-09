package com.ubirch.webui.core.structure.util

import com.ubirch.webui.core.structure.group.{ Group, GroupFactory }
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._

object Converter {

  def groupsRepresentationToGroup(groups: List[GroupRepresentation])(implicit realmName: String): List[Group] = {
    groups map { g =>
      GroupFactory.getById(g.getId)
    }
  }

  def attributesToMap(attributes: java.util.Map[String, java.util.List[String]]): Map[String, List[String]] = {
    attributes.asScala.toMap map { keyValue =>
      keyValue._1 -> keyValue._2.asScala.toList
    }
  }

}
