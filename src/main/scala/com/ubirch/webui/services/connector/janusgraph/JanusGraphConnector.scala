package com.ubirch.webui.services.connector.janusgraph

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import gremlin.scala.{ ScalaGraph, TraversalSource, _ }
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph

/**
  * Class allowing the connection to the graph contained in the JanusGraph server
  * graph: the graph
  * g: the traversal of the graph
  * cluster: the cluster used by the graph to connect to the janusgraph server
  */
protected class JanusGraphConnector extends GremlinConnector with LazyLogging with ConfigBase {

  val cluster: Cluster = GremlinConnectorFactory.buildCluster(conf)

  implicit val graph: ScalaGraph = EmptyGraph.instance.asScala.configure(_.withRemote(DriverRemoteConnection.using(cluster)))
  val g: TraversalSource = graph.traversal
  val b: Bindings = Bindings.instance

  def closeConnection(): Unit = {
    cluster.close()
  }

}
