package com.ubirch.webui

import java.util.Base64

import com.ubirch.webui.models.keycloak.Auth
import org.scalatest.Matchers
import org.scalatra.test.scalatest.ScalatraSuite

trait TestBase extends KeycloakTestContainerUtil with Matchers with ScalatraSuite {

  def generateTokenUser(username: String = "chrisx", password: String = "password"): String = {
    logger.info(Base64.getEncoder.encodeToString(password.getBytes))
    Auth.auth(username, Base64.getEncoder.encodeToString(password.getBytes))
  }

}
