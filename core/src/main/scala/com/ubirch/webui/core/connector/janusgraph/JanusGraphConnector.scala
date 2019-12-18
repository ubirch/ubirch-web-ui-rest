package com.ubirch.webui.core.connector.janusgraph

import java.io.File

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure.Util
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.janusgraph.core.{JanusGraph, JanusGraphFactory}

/**
  * Class allowing the connection to the graph contained in the JanusGraph server
  * graph: the graph
  * g: the traversal of the graph
  * cluster: the cluster used by the graph to connect to the janusgraph server
  */
protected class JanusGraphConnector extends GremlinConnector with LazyLogging with ConfigBase {

  // val cluster: Cluster = Cluster.open(GremlinConnectorFactory.buildProperties(conf))

  val trustStore: Option[File] = createOptionTrustStore()

  val janusPropsUpdates: String = trustStore match {
    case Some(trust) => janusgraphProperties.replaceAll("/etc/opt/janusgraph/truststore.jks", trust.getAbsolutePath)
    case None => janusgraphProperties
  }

  val janusProps: File = Util.createTempFile(janusPropsUpdates)

  implicit val graph: JanusGraph = JanusGraphFactory.open(janusProps.getAbsolutePath) //EmptyGraph.instance.asScala.configure(_.withRemote(DriverRemoteConnection.using(cluster)))
  val g = graph.traversal
  val b: Bindings = Bindings.instance

  def closeConnection(): Unit = {
    //cluster.close()
  }

  private def createOptionTrustStore(): Option[File] = {
    trustStoreOption match {
      case Some(trustStore) => if (!trustStore.isEmpty) {
        Option(Util.createTempFile(janusgraphProperties, Option("truststore"), Option("jks")))
      } else None
      case None => None
    }
  }

}
