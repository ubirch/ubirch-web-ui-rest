package com.ubirch.webui

import java.util.Base64

import com.ubirch.webui.core.structure.Auth
import com.ubirch.webui.test.{ Elements, EmbeddedKeycloakUtil }
import org.scalatest.{ FeatureSpec, Matchers }
import org.scalatra.test.scalatest.ScalatraSuite

class TestBase extends FeatureSpec with EmbeddedKeycloakUtil with Matchers with ScalatraSuite with Elements {

  def generateTokenUser(username: String = "chrisx", password: String = "password"): String = {
    logger.info(Base64.getEncoder.encodeToString(password.getBytes))
    Auth.auth(username, Base64.getEncoder.encodeToString(password.getBytes))
  }

}
