package com.ubirch.webui.models.keycloak.tenant

import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._

case class Tenant(id: String, name: String, attributes: Map[String, String], subTenants: List[Tenant], path: String)

object Tenant {
  implicit class GroupToTenant(groupRepresentation: GroupRepresentation) {
    def groupRepresentationToTenant: Tenant =
      Tenant(
        groupRepresentation.getId,
        groupRepresentation.getName,
        groupRepresentation.getAttributes.asScala.toMap.mapValues(_.asScala.headOption.getOrElse("")),
        Nil,
        groupRepresentation.getPath
      )
  }
}
