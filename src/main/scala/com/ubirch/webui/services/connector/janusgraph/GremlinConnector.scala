package com.ubirch.webui.services.connector.janusgraph

import gremlin.scala.{ ScalaGraph, TraversalSource }
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

trait GremlinConnector {
  def graph: ScalaGraph
  def g: TraversalSource
  def b: Bindings
  def closeConnection(): Unit
}

