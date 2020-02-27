package com.ubirch.webui.core.config

import com.typesafe.config.{ Config, ConfigFactory }

object ConfigBase {
  private val conf: Config = ConfigFactory.load()
}

trait ConfigBase {

  def conf: Config = ConfigBase.conf

  val theRealmName: String = conf.getString("keycloak.realmName")

  val keycloakServerUrl: String = conf.getString("keycloak.server.url")
  val keycloakRealm: String = conf.getString("keycloak.server.realm")
  val keycloakUsername: String = conf.getString("keycloak.server.username")
  val keycloakPassword: String = conf.getString("keycloak.server.password")
  val keycloakClientId: String = conf.getString("keycloak.server.clientId")

  val keyCloakJson: String = conf.getString("keycloak.jsonString")
  val timeToWait: Int = conf.getInt("core.timeToWaitDevices")

}
