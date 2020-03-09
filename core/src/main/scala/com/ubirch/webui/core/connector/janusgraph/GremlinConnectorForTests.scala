package com.ubirch.webui.core.connector.janusgraph

import gremlin.scala.{ ScalaGraph, TraversalSource }
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

protected class GremlinConnectorForTests extends GremlinConnector {

  implicit val graph: ScalaGraph = ??? //TinkerGraph.open().asScala
  val g: TraversalSource = ??? // graph.traversal
  val b: Bindings = Bindings.instance

  override def closeConnection(): Unit = {} //graph.close()

}
