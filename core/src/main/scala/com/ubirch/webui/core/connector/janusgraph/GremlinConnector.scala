package com.ubirch.webui.core.connector.janusgraph

import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.janusgraph.core.JanusGraph

trait GremlinConnector {
  def graph: JanusGraph
  def g: GraphTraversalSource
  def b: Bindings
  def closeConnection()
}

