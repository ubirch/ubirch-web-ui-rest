package com.ubirch.webui.models.graph

import java.util
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.services.connector.janusgraph.GremlinConnector
import gremlin.scala.{ Key, P }
import gremlin.scala.GremlinScala.Aux
import shapeless.HNil

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

trait GraphClient {

  /**
    * @return the number of UPPs that a device has created during the specified timeframe
    */
  def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState]

  /**
    * Will query the graph backend to find the last-hash property value contained on the graph
    * If found, it'll traverse the graph to find the last n hashes
    * If it is not found, will return a failed LastHash structure
    * @return a list of LastHash object containing a hash and timestamp (if found).
    */
  def getLastHashes(hwDeviceId: String, n: Int): Future[List[LastHash]]
}

class DefaultGraphClient(gc: GremlinConnector) extends GraphClient with LazyLogging {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState] = {

    val futureCount = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .inE("UPP->DEVICE")
      .has(Key[Date]("timestamp"), P.inside(convertToDate(from), convertToDate(to)))
      .count()
      .promise()

    futureCount.map(r => UppState(hwDeviceId, from, to, r.head.toInt))
  }

  def getLastHashes(hwDeviceId: String, n: Int): Future[List[LastHash]] = {

    logger.debug(s"LastHash: Looking for last hashes of $hwDeviceId")
    val futureMaybeLastHash = gc.g
      .V()
      .has(Key[String]("device_id"), hwDeviceId)
      .value(Key[String]("last_hash"))
      .promise()

    futureMaybeLastHash flatMap { maybeLastHash =>
      logger.debug(s"LastHash: For $hwDeviceId found last hash in elementProperty: $maybeLastHash")
      if (maybeLastHash.isEmpty) {
        Future.successful(Nil)
      } else {
        val futureMaybeLastNUppsValuesMap = try {
          lastNHashesTraversal(maybeLastHash.head, n).promise()
        } catch {
          case e: NullPointerException => {
            logger.error(s"LastHash: nullPointerException, hash = ${maybeLastHash.head}, n = $n", e)
            Future.successful(List(new util.HashMap[AnyRef, AnyRef]()))
          }

        }
        futureMaybeLastNUppsValuesMap.map { maybeLastNUppsValuesMap =>
          /* if none are found, then it means that there's less than n upps produced by the device
            then we check how many were created (should be an inexpensive measure as a number of vertices < n has been created)
            and we use this number to return this amount of upps queried */
          if (maybeLastNUppsValuesMap.isEmpty) {
            val maybeNumberOfUpps = gc.g
              .V()
              .has(Key[String]("hash"), maybeLastHash.head)
              .repeat(_.out("CHAIN"))
              .limit(n.toLong)
              .count()
              .promise()
            maybeNumberOfUpps.flatMap { numberOfUpps =>
              logger.debug(s"LastHash: device has only ${numberOfUpps.head} but request last $n upps. Changing that.")
              lastNHashesTraversal(maybeLastHash.head, numberOfUpps.head.intValue()).promise()
            }
          } else {
            logger.debug("LastHash: converting found hashes with valueMapToLastHash")
            futureMaybeLastNUppsValuesMap
          }
        }.flatten.map(valuesMap => for {
          valueMap <- valuesMap
        } yield {
          valueMapToLastHash(hwDeviceId, valueMap)
        })

      }
    }

  }

  private def convertToDate(dateAsLong: Long) = new java.util.Date(dateAsLong)

  private def lastNHashesTraversal(hash: String, n: Int): Aux[util.Map[AnyRef, AnyRef], HNil] = {
    // special case for 1, as there's yet no chain
    if (n == 1) {
      gc.g
        .V()
        .has(Key[String]("hash"), hash)
        .elementMap
    } else {
      gc.g
        .V()
        .has(Key[String]("hash"), hash)
        .repeat(_.out("CHAIN"))
        .emit()
        .limit(n.toLong)
        .path()
        .tail()
        .unfold()
        .elementMap
    }

  }

  private def valueMapToLastHash(hwDeviceId: String, valueMap: java.util.Map[AnyRef, AnyRef]): LastHash = {
    val mapScala = valueMap.asScala
    val maybeHash = if (mapScala.contains("hash")) Some(mapScala("hash").asInstanceOf[String]) else {
      logger.warn(s"LastHash: found none in hash for lastHash when processing lastHashes of device $hwDeviceId: valueMap = ${valueMap.asScala.mkString(", ")}")
      None
    }
    val maybeTimestamp = if (mapScala.contains("timestamp")) Some(mapScala("timestamp").asInstanceOf[Date]) else {
      logger.warn(s"LastHash: found none in timestamp for lastHash when processing lastHashes of device $hwDeviceId: valueMap = ${valueMap.asScala.mkString(", ")}")
      None
    }
    LastHash(hwDeviceId, maybeHash, maybeTimestamp)
  }
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
    val timeAsString = LastHash.sdf.format(time)
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

object LastHash {
  import java.text.SimpleDateFormat
  import java.util.TimeZone
  val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
  sdf.setTimeZone(TimeZone.getTimeZone("GMT"))
}
