package com.ubirch.webui.services.connector.janusgraph

import com.typesafe.config.Config
import com.ubirch.webui.services.connector.janusgraph.ConnectorType.ConnectorType
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.ser.{ GraphBinaryMessageSerializerV1, Serializers }

import java.util

object GremlinConnectorFactory {

  private lazy val instanceTest = new GremlinConnectorForTests
  private lazy val instanceJanusGraph = new JanusGraphConnector

  def getInstance(connectorType: ConnectorType): GremlinConnector = {
    connectorType match {
      case ConnectorType.JanusGraph => instanceJanusGraph
      case ConnectorType.Test => instanceTest
    }
  }

  def buildCluster(config: Config): Cluster = {
    val cluster = Cluster.build()
    val hosts: List[String] = config.getString("janus.connector.hosts")
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)

    cluster.addContactPoints(hosts: _*)
      .port(config.getInt("janus.connector.port"))
    val maxWaitForConnection = config.getInt("janus.connector.connectionPool.maxWaitForConnection")
    if (maxWaitForConnection > 0) cluster.maxWaitForConnection(maxWaitForConnection)

    val reconnectInterval = config.getInt("janus.connector.connectionPool.reconnectInterval")
    if (reconnectInterval > 0) cluster.reconnectInterval(reconnectInterval)

    val conf = new util.HashMap[String, AnyRef]()
    conf.put("ioRegistries", config.getAnyRef("janus.connector.serializer.config.ioRegistries").asInstanceOf[java.util.ArrayList[String]])
    val serializer = Serializers.GRAPHBINARY_V1D0.simpleInstance()
    serializer.configure(conf, null)

    cluster.serializer(serializer).create()
  }
}
