package com.ubirch.webui.core.connector

import org.keycloak.admin.client.Keycloak

object KeyCloakConnector {
  private val instance = new KeyCloakConnector
  def get: KeyCloakConnector = instance
}

class KeyCloakConnector private () {
  val kc: Keycloak = Keycloak.getInstance("http://localhost:8080/auth",
    "master",
    "adim",
    "admin",
    "admin-cli")
}
