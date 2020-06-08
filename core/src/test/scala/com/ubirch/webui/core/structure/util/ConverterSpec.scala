package com.ubirch.webui.core.structure.util

import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers }

import scala.util.Random

class ConverterSpec extends FeatureSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  def giveMeRandomString(size: Int = 32): String = {
    Random.alphanumeric.take(size).mkString
  }
}
