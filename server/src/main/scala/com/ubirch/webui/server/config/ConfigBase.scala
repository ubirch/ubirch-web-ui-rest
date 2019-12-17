package com.ubirch.webui.server.config

import com.typesafe.config.{Config, ConfigFactory}

trait ConfigBase {
  private def conf: Config = ConfigFactory.load()

  val serverPort: Int = conf.getInt("server.port")
  val serverBaseUrl: String = conf.getString("server.baseUrl")
  val appVersion: String = conf.getString("app.version")
  val swaggerPath: String = conf.getString("server.swaggerPath")

  val scalatraEnv: String = conf.getString("server.scalatra.environment")
}
