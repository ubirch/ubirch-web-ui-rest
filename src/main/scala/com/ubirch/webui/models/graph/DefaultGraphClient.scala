package com.ubirch.webui.models.graph

import java.util.Date

import com.ubirch.webui.models.keycloak.member.{ LastHash, UppState }
import com.ubirch.webui.services.connector.janusgraph.GremlinConnector
import gremlin.scala.{ Key, P }

import scala.concurrent.{ ExecutionContext, Future }

trait GraphClient {

  /**
    * @return the number of UPPs that a device has created during the specified timeframe
    */
  def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState]

  /**
    * Will query the graph backend to find the last-hash property value contained on the graph
    * If it is not found, will return a failed LastHash structure
    * @return a LastHash object containing the last hash (if found).
    */
  def getLastHash(hwDeviceId: String): Future[LastHash]

}

class DefaultGraphClient(gc: GremlinConnector) extends GraphClient {

  implicit val ec: ExecutionContext = ExecutionContext.global

  /**
    * Return the number of UPPs that a device has created during the specified timeframe
    * @return
    */
  def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState] = {

    val futureCount = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .inE("UPP->DEVICE")
      .has(Key[Date]("timestamp"), P.inside(convertToDate(from), convertToDate(to)))
      .count()
      .promise()

    futureCount.map(r => UppState(hwDeviceId, from, to, r.head.toInt))
  }

  def getLastHash(hwDeviceId: String): Future[LastHash] = {

    val gremlinQueryResult = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .value(Key[String]("last_hash")).promise()

    gremlinQueryResult.map(r => LastHash(hwDeviceId, r.headOption))
  }

  private def convertToDate(dateAsLong: Long) = new java.util.Date(dateAsLong)

}
