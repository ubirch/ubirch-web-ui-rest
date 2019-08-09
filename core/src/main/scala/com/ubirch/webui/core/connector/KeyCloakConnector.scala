package com.ubirch.webui.core.connector

import org.keycloak.admin.client.Keycloak

object KeyCloakConnector {
  private val instance = new KeyCloakConnector
  def get: KeyCloakConnector = instance
}

// TODO: think of a way to change clint dynamically without creating multiple KeyCloak instances
// for the moment, we're connecting through the admin-cli
class KeyCloakConnector private () {
  val kc: Keycloak = Keycloak.getInstance("http://localhost:8080/auth",
    "test-realm",
    "adim",
    "admin",
    "admin-cli")
}

