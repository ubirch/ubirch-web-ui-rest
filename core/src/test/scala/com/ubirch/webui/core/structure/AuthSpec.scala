package com.ubirch.webui.core.structure

import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ BeforeAndAfterEach, FeatureSpec, Matchers }

class AuthSpec extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach {
  feature("convert password") {
    scenario("B64 password to plaintext -> OK") {
      val utf8Pwd = "djefpigort"
      val b64Pwd = Base64.getEncoder.encodeToString(utf8Pwd.getBytes)
      println(b64Pwd)
      val res = Auth.stringToB64(b64Pwd)
      res shouldBe utf8Pwd
    }
  }
}

