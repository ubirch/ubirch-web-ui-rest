package com.ubirch.webui.core.config

import com.typesafe.config.{ Config, ConfigFactory }

trait ConfigBase {
  def conf: Config = ConfigFactory.load()
}
