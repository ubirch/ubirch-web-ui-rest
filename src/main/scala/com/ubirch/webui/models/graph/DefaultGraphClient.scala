package com.ubirch.webui.models.graph

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.services.connector.janusgraph.GremlinConnector
import gremlin.scala.{ Key, P }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

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

  def getLastNHashes(hwDeviceId: String, n: Int): Future[List[LastHash]]
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

  override def getLastNHashes(hwDeviceId: String, n: Int): Future[List[LastHash]] = {
    val futureLastHash = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .value(Key[String]("last_hash")).promise()

    val res = for {
      lastHash <- futureLastHash
    } yield {
      gc.g.V()
        .has(Key[String]("hash"), lastHash.head)
        .repeat(_.out("CHAIN"))
        .times(n - 1)
        .path()
        .unfold()
        .elementMap
        .promise()
    }

    val res2 = res.flatten

    for {
      result <- res2
    } yield {
      for {
        map <- result
      } yield {
        val maybeHash = Try(map.get("hash").asInstanceOf[String]).toOption
        val maybeTimestamp = Try(map.get("timestamp").asInstanceOf[Date]).toOption
        LastHash(hwDeviceId, maybeHash, maybeTimestamp)
      }
    }

  }

  private def convertToDate(dateAsLong: Long) = new java.util.Date(dateAsLong)

}

case class UppState(hwDeviceId: String, from: Long, to: Long, numberUpp: Int) {
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = ("deviceId" -> hwDeviceId) ~
      ("numberUPPs" -> numberUpp) ~
      ("from" -> from) ~
      ("to" -> to)
    compact(render(json))
  }
}

case class LastHash(hwDeviceId: String, maybeHash: Option[String], maybeTimestamp: Option[Date] = None) extends LazyLogging {
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._
  override def toString: String = compact(render(toJson))

  def toJson = {

    val time = maybeTimestamp.getOrElse(new Date(0))
    import java.text.SimpleDateFormat
    import java.util.TimeZone
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    sdf.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timeAsString = sdf.format(time)
    logger.info("lastHash: " + maybeHash.getOrElse("none"))
    maybeHash match {
      case Some(hash) =>
        ("deviceId" -> hwDeviceId) ~
          ("hash" -> hash) ~
          ("timestamp" -> timeAsString)
      case None =>
        ("deviceId" -> hwDeviceId) ~
          ("hash" -> "") ~
          ("timestamp" -> "")
    }
  }
}
