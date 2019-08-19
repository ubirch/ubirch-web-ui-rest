package com.ubirch.webui.server

import java.net.URL

import com.ubirch.webui.core.config.ConfigBase
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object Boot extends ConfigBase {
  def main(args: Array[String]) {
    val server = new Server(conf.getInt("server.getPort"))
    val context = new WebAppContext()
    context.setServer(server)
    context.setContextPath("/")
    val confPath: URL = getClass.getResource("/")

    context.setWar(confPath.getPath)
    server.setHandler(context)

    try {
      server.start()
      server.join()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}
