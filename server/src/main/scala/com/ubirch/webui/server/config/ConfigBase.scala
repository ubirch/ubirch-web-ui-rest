package com.ubirch.webui.server.config

import com.typesafe.config.{ Config, ConfigFactory }

object ConfigBase {
  private val conf: Config = ConfigFactory.load()
}

trait ConfigBase {

  def conf: Config = ConfigBase.conf

  val serverPort: Int = conf.getInt("server.port")
  val serverBaseUrl: String = conf.getString("server.baseUrl")
  val appVersion: String = conf.getString("app.version")
  val swaggerPath: String = conf.getString("server.swaggerPath")

  val scalatraEnv: String = conf.getString("server.scalatra.environment")
}
