package com.ubirch.webui.core

import java.time.{Instant, ZonedDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.connector.janusgraph.{ConnectorType, GremlinConnectorFactory}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, Matchers}

class JanusSpec extends FeatureSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with LazyLogging {

  feature("get jg info") {
    scenario("stuff") {
      // original: from 2019-11-28T17:13:49.494+0000 to 2019-12-16T19:15:50.298+0000
      // "v.timestamp:[\"2019-11-28T17:13:49.494+0000\" TO \"2019-12-16T19:15:50.298+0000\"] AND v.\"owner_id\":HZRbNO3LG3F5PRfbi5VGGS6s1n8pPbWG"

      //val formatter = DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss:SSS")
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+0000'")

      implicit val gc = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)
      val t0 = Instant.ofEpochMilli(1574961229494L)
      val t0c = ZonedDateTime.ofInstant(t0, ZoneOffset.UTC).format(formatter)
      val t1 = Instant.ofEpochMilli(1578971229494L)
      val t1c = ZonedDateTime.ofInstant(t1, ZoneOffset.UTC).format(formatter)
      val ownerId = "HZRbNO3LG3F5PRfbi5VGGS6s1n8pPbWG"

      val query = "v.timestamp:[\"" + t0c.toString + "\" TO \"" + t1c.toString + "\"] AND v.\"owner_id\":\"" + ownerId + "\""
      val q2 = "v.timestamp:[\"2019-11-28T17:13:49.494+0000\" TO \"2019-12-16T19:15:50.298+0000\"] AND v.\"owner_id\":HZRbNO3LG3F5PRfbi5VGGS6s1n8pPbWG"
      logger.info(s"query1 = ${query}")
      logger.info(s"query2 = ${q2}")
      val b = gc.graph.indexQuery("indexTimestampAndOwner", query).vertexTotals()
      println("b: " + b)
    }
  }

}
