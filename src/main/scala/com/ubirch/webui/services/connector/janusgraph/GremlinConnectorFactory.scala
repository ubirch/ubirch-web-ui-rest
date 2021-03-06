package com.ubirch.webui.services.connector.janusgraph

import com.typesafe.config.Config
import com.ubirch.webui.services.connector.janusgraph.ConnectorType.ConnectorType
import org.apache.commons.configuration.PropertiesConfiguration

object GremlinConnectorFactory {

  private lazy val instanceTest = new GremlinConnectorForTests
  private lazy val instanceJanusGraph = new JanusGraphConnector

  def getInstance(connectorType: ConnectorType): GremlinConnector = {
    connectorType match {
      case ConnectorType.JanusGraph => instanceJanusGraph
      case ConnectorType.Test => instanceTest
    }
  }

  def buildProperties(config: Config): PropertiesConfiguration = {
    val conf = new PropertiesConfiguration()
    conf.addProperty("hosts", config.getString("janus.connector.hosts"))
    conf.addProperty("port", config.getString("janus.connector.port"))
    conf.addProperty("serializer.className", config.getString("janus.connector.serializer.className"))
    conf.addProperty("connectionPool.maxWaitForConnection", config.getString("janus.connector.connectionPool.maxWaitForConnection"))
    conf.addProperty("connectionPool.reconnectInterval", config.getString("janus.connector.connectionPool.reconnectInterval"))
    // no idea why the following line needs to be duplicated. Doesn't work without
    // cf https://stackoverflow.com/questions/45673861/how-can-i-remotely-connect-to-a-janusgraph-server first answer, second comment ¯\_ツ_/¯
    conf.addProperty("serializer.config.ioRegistries", config.getAnyRef("janus.connector.serializer.config.ioRegistries").asInstanceOf[java.util.ArrayList[String]])
    conf.addProperty("serializer.config.ioRegistries", config.getStringList("janus.connector.serializer.config.ioRegistries"))
    conf
  }
}
