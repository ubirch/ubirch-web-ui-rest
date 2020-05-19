package com.ubirch.webui.core.structure.util

import java.util.UUID

import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

class UtilSpec extends FeatureSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

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
