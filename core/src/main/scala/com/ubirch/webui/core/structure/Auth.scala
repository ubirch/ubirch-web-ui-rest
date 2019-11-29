package com.ubirch.webui.core.structure

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.Exceptions.{ HexDecodingError, NotAuthorized }
import org.keycloak.authorization.client.AuthzClient

object Auth extends LazyLogging with ConfigBase {

  /**
    * Authenticate against keycloak
    * @param hwDeviceId: Username of the device
    * @param password: B64 encoded password
    * @return Auth token
    */
  def auth(hwDeviceId: String, password: String): String = {
    val passwordRaw = decodeB64String(password)
    val jsonString = keyCloakJson
    val authzClient = createAuthorisationClient(jsonString)
    try {
      authzClient.obtainAccessToken(hwDeviceId, passwordRaw).getToken
    } catch {
      case _: org.keycloak.authorization.client.util.HttpResponseException => throw NotAuthorized("Invalid username / password")
    }
  }

  private def createAuthorisationClient(keyCloakJson: String): AuthzClient = {
    val jsonKeycloakStream = new ByteArrayInputStream(keyCloakJson.getBytes(StandardCharsets.UTF_8))
    AuthzClient.create(jsonKeycloakStream)
  }

  def decodeB64String(str: String): String = {
    try {
      val stringBytes = Base64.getDecoder.decode(str)
      new String(stringBytes, "UTF-8")
    } catch {
      case e: Throwable => throw HexDecodingError(e.getMessage)
    }
  }
}
