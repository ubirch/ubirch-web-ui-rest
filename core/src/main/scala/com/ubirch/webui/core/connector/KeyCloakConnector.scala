package com.ubirch.webui.core.connector

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.apache.commons.configuration.PropertiesConfiguration
import org.keycloak.admin.client.Keycloak

object KeyCloakConnector extends ConfigBase {
  private val instance = new KeyCloakConnector
  def get: KeyCloakConnector = instance

  def buildProperties(config: Config): PropertiesConfiguration = {
    val conf = new PropertiesConfiguration()
    conf.addProperty("serverUrl", keycloakServerUrl)
    conf.addProperty("realm", keycloakRealm)
    conf.addProperty("username", keycloakUsername)
    conf.addProperty("password", keycloakPassword)
    conf.addProperty("clientId", keycloakClientId)
    conf
  }
}

// TODO: think of a way to change client dynamically without creating multiple KeyCloak instances
// for the moment, we're connecting through the admin-cli, not good
class KeyCloakConnector private () extends LazyLogging with ConfigBase {

  val connector: Keycloak = Keycloak.getInstance(
    keycloakServerUrl,
    keycloakRealm,
    keycloakUsername,
    keycloakPassword,
    keycloakClientId
  )
}

