package com.ubirch.webui.core.operations

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.keycloak.authorization.client.AuthzClient

object Auth extends LazyLogging with ConfigBase {

  def auth(hwDeviceId: String, password: String): Unit = {

    val jsonString = conf.getString("keycloak.jsonString")
    logger.debug(conf.getString("keycloak.server.url"))
    logger.debug(jsonString)
    val jsonKeycloakStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))
    val authzClient = AuthzClient.create(jsonKeycloakStream)
    authzClient.obtainAccessToken(hwDeviceId, password)
  }

}
