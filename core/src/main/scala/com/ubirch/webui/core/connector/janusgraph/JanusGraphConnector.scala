package com.ubirch.webui.core.connector.janusgraph

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

  val t = Util.createTempFile(janusgraphProperties)

  implicit val graph: JanusGraph = JanusGraphFactory.open(t.getAbsolutePath) //EmptyGraph.instance.asScala.configure(_.withRemote(DriverRemoteConnection.using(cluster)))
  val g = graph.traversal
  val b: Bindings = Bindings.instance

  def closeConnection(): Unit = {
    //cluster.close()
  }

}
