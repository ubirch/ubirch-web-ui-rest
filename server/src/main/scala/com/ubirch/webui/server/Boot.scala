package com.ubirch.webui.server

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.resource.ResourceCollection
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object Boot extends ConfigBase with LazyLogging {
  def main(args: Array[String]) {
    val server = new Server(conf.getInt("server.port"))
    val context = new WebAppContext()

    val baseUrl = conf.getString("server.baseUrl")
    val version = "/v1"

    context.setContextPath(baseUrl + version)

    val resources = new ResourceCollection(Array[String]("server/src/main/scala", "server/src/main/swagger-ui"))
    // context.setResourceBase("src/main/scala")
    context.setBaseResource(resources)

    context.addEventListener(new ScalatraListener)


    context.addServlet(classOf[DefaultServlet], "/")

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
