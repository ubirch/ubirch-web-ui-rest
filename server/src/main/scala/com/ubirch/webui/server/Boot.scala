package com.ubirch.webui.server

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object Boot extends ConfigBase with LazyLogging {
  def main(args: Array[String]) {
    val server = new Server(conf.getInt("server.port"))
    val context = new WebAppContext()
    context.setServer(server)
    context.setContextPath("/")
    val domain = getClass.getProtectionDomain
    val location = domain.getCodeSource.getLocation
    context.setWar(location.toExternalForm)
    server.setHandler(context)

    try {
      server.start()
      server.join()
    } catch {
      case e: Exception =>
        logger.error(e.getMessage)
        System.exit(-1)
    }
  }
}
