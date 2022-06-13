package com.ubirch.webui.models.keycloak.tenant

case class Tenant(id: String, name: String, attributes: Map[String, String], subTenants: List[Tenant])
