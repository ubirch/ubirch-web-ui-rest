package com.ubirch.webui.services.connector.keycloak

import com.ubirch.webui.config.ConfigBase

import javax.inject.Singleton

trait KeycloakConfig {
  val serverUrl: String
  val realm: String
  val username: String
  val password: String
  val clientId: String
}

@Singleton
class DefaultKeycloakConfig extends KeycloakConfig with ConfigBase {
  val serverUrl: String = keycloakServerUrl
  val realm: String = keycloakRealm
  val username: String = keycloakUsername
  val password: String = keycloakPassword
  val clientId: String = keycloakClientId
}
