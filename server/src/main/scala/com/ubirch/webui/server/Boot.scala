package com.ubirch.webui.server

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object Boot extends ConfigBase with LazyLogging {

  def main(args: Array[String]) {

    val server = new Server(conf.getInt("server.port"))

    val baseUrl = conf.getString("server.baseUrl")
    val version = "/" + conf.getString("app.version")

    // context for main scalatra rest API
    val context: WebAppContext = new WebAppContext()
    context.setContextPath(baseUrl + version)
    context.setResourceBase("src/main/scala")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    // context for swagger-ui
    val context2 = new WebAppContext()
    context2.setContextPath(baseUrl + version + "/docs")
    context2.setResourceBase(conf.getString("server.swaggerPath"))

    val contexts = new ContextHandlerCollection()
    contexts.setHandlers(Array(context, context2))
    server.setHandler(contexts)

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
