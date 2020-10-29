package com.ubirch.webui.config

import com.typesafe.config.{ Config, ConfigFactory }

trait ConfigBase {

  def conf: Config = ConfigBase.conf

  val serverPort: Int = conf.getInt("server.port")
  val serverBaseUrl: String = conf.getString("server.baseUrl")
  val appVersion: String = conf.getString("app.version")
  val swaggerPath: String = conf.getString("server.swaggerPath")
  val scalatraEnv: String = conf.getString("server.scalatra.environment")

  val theRealmName: String = conf.getString("keycloak.realmName")
  val keycloakServerUrl: String = conf.getString("keycloak.server.url")
  val keycloakRealm: String = conf.getString("keycloak.server.realm")
  val keycloakUsername: String = conf.getString("keycloak.server.username")
  val keycloakPassword: String = conf.getString("keycloak.server.password")
  val keycloakClientId: String = conf.getString("keycloak.server.clientId")
  val keyCloakJson: String = conf.getString("keycloak.jsonString")
  val timeToWait: Int = conf.getInt("core.timeToWaitDevices")

  val sdsBaseUrl: String = conf.getString("simpleDataService.url")
}

object ConfigBase {
  val conf: Config = ConfigFactory.load()
}
