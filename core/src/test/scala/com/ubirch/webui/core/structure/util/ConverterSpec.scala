package com.ubirch.webui.core.structure.util

import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }
import java.util.UUID

import com.ubirch.webui.core.structure.Elements

import scala.util.Random

class ConverterSpec extends FeatureSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  feature("Convert UUID") {
    scenario("convert a string to a device username") {
      val str = "coucou"
      Converter.transformUuidToDeviceUsername(str) shouldBe Elements.DEVICE_PREFIX + "coucou" + Elements.DEVICE_SUFFIX
    }

    scenario("convert a string back -> SUCCESS") {
      val str = Elements.DEVICE_PREFIX + "coucou" + Elements.DEVICE_SUFFIX
      Converter.untransformUuidToDeviceUsername(str) shouldBe Some("coucou")
    }

    scenario("convert a string back -> FAIL: missing prefix") {
      val str = "coucou" + Elements.DEVICE_SUFFIX
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }

    scenario("convert a string back -> FAIL: missing suffix") {
      val str = Elements.DEVICE_PREFIX + "coucou"
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }

    scenario("convert a string back -> FAIL: missing both prefix and suffix") {
      val str = "coucou"
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }

    scenario("convert a string back -> FAIL: wrong prefix") {
      val str = giveMeRandomString(Elements.DEVICE_PREFIX.length) + "coucou" + Elements.DEVICE_SUFFIX
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }

    scenario("convert a string back -> FAIL: wrong suffix") {
      val str = Elements.DEVICE_PREFIX + "coucou" + giveMeRandomString(Elements.DEVICE_SUFFIX.length)
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }

    scenario("convert a string back -> FAIL: wrong prefix and suffix") {
      val str = giveMeRandomString(Elements.DEVICE_SUFFIX.length) + "coucou" + giveMeRandomString(Elements.DEVICE_SUFFIX.length)
      Converter.untransformUuidToDeviceUsername(str) shouldBe None
    }
  }

  def giveMeRandomString(size: Int = 32): String = {
    Random.alphanumeric.take(size).mkString
  }
}
