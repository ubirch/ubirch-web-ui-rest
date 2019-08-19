package com.ubirch.webui.core.connector

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.apache.commons.configuration.PropertiesConfiguration
import org.keycloak.admin.client.Keycloak

object KeyCloakConnector {
  private val instance = new KeyCloakConnector
  def get: KeyCloakConnector = instance

  def buildProperties(config: Config): PropertiesConfiguration = {
    val conf = new PropertiesConfiguration()
    conf.addProperty("serverUrl", config.getString("keycloak.server.url"))
    conf.addProperty("realm", config.getString("keycloak.server.realm"))
    conf.addProperty("username", config.getString("keycloak.server.username"))
    conf.addProperty("password", config.getString("keycloak.server.password"))
    conf.addProperty("clientId", config.getString("keycloak.server.clientId"))
    conf
  }
}

// TODO: think of a way to change client dynamically without creating multiple KeyCloak instances
// for the moment, we're connecting through the admin-cli, not good
class KeyCloakConnector private() extends LazyLogging with ConfigBase {

  val kc: Keycloak = Keycloak.getInstance(
    conf.getString("keycloak.server.url"),
    conf.getString("keycloak.server.realm"),
    conf.getString("keycloak.server.username"),
    conf.getString("keycloak.server.password"),
    conf.getString("keycloak.server.clientId")
  )
}

