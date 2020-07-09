package com.ubirch.webui.models.keycloak

import java.util.UUID

import com.ubirch.webui.models.keycloak.util.Util
import org.scalatest.{ FeatureSpec, Matchers }

class UtilSpec extends FeatureSpec with Matchers {

  feature("Convert UUID") {
    scenario("check if string is UUID -> SUCCESS") {
      val str = UUID.randomUUID().toString
      Util.isStringUuid(str) shouldBe true
    }

    scenario("try to convert a random string to UUID -> FAIL") {
      val str = "fhjibdigjbkfdbgfjbd"
      Util.isStringUuid(str) shouldBe false
    }
  }

}
