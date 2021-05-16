package com.ubirch.webui

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.batch.Elephant
import com.ubirch.webui.config.ConfigBase
import org.eclipse.jetty.server.{ HttpConnectionFactory, Server }
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object Boot extends ConfigBase with LazyLogging {

  def disableServerVersionHeader(server: Server): Unit = {
    server.getConnectors.foreach { connector =>
      connector.getConnectionFactories
        .stream()
        .filter(cf => cf.isInstanceOf[HttpConnectionFactory])
        .forEach(cf => cf.asInstanceOf[HttpConnectionFactory].getHttpConfiguration.setSendServerVersion(false))
    }
  }

  def main(args: Array[String]): Unit = {

    Elephant.start()

    val server = new Server(serverPort)
    disableServerVersionHeader(server)

    val baseUrl = serverBaseUrl
    val version = "/" + appVersion

    // context for main scalatra rest API
    val scalatraContext: WebAppContext = new WebAppContext()
    scalatraContext.setContextPath(baseUrl + version)
    scalatraContext.setResourceBase("src/main/scala")
    scalatraContext.addEventListener(new ScalatraListener)
    scalatraContext.addServlet(classOf[DefaultServlet], "/")

    // context for swagger-ui
    val swaggerContext = new WebAppContext()
    swaggerContext.setContextPath(baseUrl + version + "/docs")
    swaggerContext.setResourceBase(swaggerPath)

    val contexts = new ContextHandlerCollection()
    contexts.setHandlers(Array(scalatraContext, swaggerContext))
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
