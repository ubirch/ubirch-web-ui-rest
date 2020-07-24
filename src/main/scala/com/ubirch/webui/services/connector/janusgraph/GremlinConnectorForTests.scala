package com.ubirch.webui.services.connector.janusgraph

import gremlin.scala.{ ScalaGraph, TraversalSource }
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

protected class GremlinConnectorForTests extends GremlinConnector {

  implicit val graph: ScalaGraph = null //TinkerGraph.open().asScala
  val g: TraversalSource = null // graph.traversal
  val b: Bindings = Bindings.instance

  override def closeConnection(): Unit = {} //graph.close()

}
