package com.ubirch.webui.models.keycloak.tenant

case class Device(keycloakId: String, deviceId: String, description: String, attributes: Map[String, String])
