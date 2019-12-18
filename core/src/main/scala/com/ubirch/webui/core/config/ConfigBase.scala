package com.ubirch.webui.core.config

import com.typesafe.config.{Config, ConfigFactory}

trait ConfigBase {
  def conf: Config = ConfigFactory.load()

  val theRealmName = conf.getString("keycloak.realmName")

  val janusgraphPropetiesFilePath = conf.getString("janus.properties")

  val keycloakServerUrl: String = conf.getString("keycloak.server.url")
  val keycloakRealm: String = conf.getString("keycloak.server.realm")
  val keycloakUsername: String = conf.getString("keycloak.server.username")
  val keycloakPassword: String = conf.getString("keycloak.server.password")
  val keycloakClientId: String = conf.getString("keycloak.server.clientId")

  val keyCloakJson: String = conf.getString("keycloak.jsonString")
  val timeToWait: Int = conf.getInt("core.timeToWaitDevices")

}
