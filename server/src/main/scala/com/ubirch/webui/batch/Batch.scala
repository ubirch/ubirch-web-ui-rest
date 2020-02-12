package com.ubirch.webui.batch

import java.io.{ BufferedReader, InputStream, InputStreamReader }
import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.util.JsonHelper
import com.ubirch.webui.server.config.ConfigBase
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }
import org.json4s.JsonAST.JValue
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.jackson.Serialization._
import org.scalatra.servlet.FileItem

import scala.collection.JavaConverters._

sealed trait Batch[D] {
  val value: Symbol
  val separator: String
  def data(line: String, separator: String): Either[String, D]
  def ingest(fileItem: FileItem, withHeader: Boolean, description: String, batchType: Symbol, tags: String): ReadStatus
}

case class ReadStatus(status: Boolean, processed: Int, success: Int, failure: Int, failures: List[String])

object Batch extends StrictLogging {
  def isValid(value: String): Boolean = fromString(value).isDefined
  def fromString(value: String): Option[Batch[_]] = options.find(_.value.name == value)
  val options: List[Batch[_]] = List(SIM)

  def read(inputStream: InputStream, skipHeader: Boolean)(f: String => Either[String, _]): ReadStatus = {

    var isr: InputStreamReader = null
    var br: BufferedReader = null

    var processed = 0
    var success = 0
    var failure = 0

    val failureMessages = scala.collection.mutable.ListBuffer.empty[String]

    try {
      isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
      br = new BufferedReader(isr)
      val it = br.lines().iterator().asScala

      while (it.hasNext) {

        if (skipHeader && processed == 0) {}
        else {
          val value = it.next()
          if (value.nonEmpty) {
            f(value) match {
              case Right(_) => success = success + 1
              case Left(error) =>
                failure = failure + 1
                failureMessages += error
            }
          }
        }

        processed = processed + 1
      }

      ReadStatus(status = true, processed, success, failure, failureMessages.toList)

    } catch {
      case e: Exception =>
        logger.error("Error processing stream [{}]", e.getMessage)
        ReadStatus(status = false, processed, success, failure, failureMessages.toList)
    } finally {
      if (br != null) br.close()
      if (isr != null) isr.close()
    }

  }

}

object BatchRequestSerializer extends Serializer[BatchRequest] {

  implicit val formats: Formats = DefaultFormats

  override def serialize(topic: String, data: BatchRequest): Array[Byte] = {
    write(data).getBytes(StandardCharsets.UTF_8)
  }

}

object Producer extends ExpressProducer[String, BatchRequest] with ConfigBase {

  lazy val production: ProducerRunner[String, BatchRequest] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
  val producerBootstrapServers: String = conf.getString("batch.kafkaProducer.bootstrapServers")
  val producerTopic: String = conf.getString("batch.kafkaProducer.topic")
  val lingerMs: Int = conf.getInt("batch.kafkaProducer.lingerMS")
  val keySerializer: Serializer[String] = new StringSerializer
  val valueSerializer: Serializer[BatchRequest] = BatchRequestSerializer

}

case class SIMData(provider: String, imsi: String, pin: String, cert: String)

case object SIM extends Batch[SIMData] with ConfigBase with StrictLogging {

  override val value: Symbol = 'sim_import

  override val separator: String = conf.getString("batch.separator")

  override def data(line: String, separator: String): Either[String, SIMData] = {
    line.split(separator).toList match {
      case List(provider, imsi, pin, cert) =>
        Right(SIMData(provider, imsi, pin, cert))
      case _ =>
        logger.error("Error processing line [{}]", line)
        Left(s"Error processing line [$line]")
    }
  }

  override def ingest(fileItem: FileItem, skipHeader: Boolean, description: String, batchType: Symbol, tags: String): ReadStatus = {
    import Producer._

    val readStatus = Batch.read(fileItem.getInputStream, skipHeader) { line =>
      data(line, separator).map { d =>
        val jv = JsonHelper.ToJson[SIMData](d).get
        val br = BatchRequest(fileItem.name, description, batchType, tags, jv)
        send(producerTopic, br)
      }
    }

    readStatus

  }

}

case class BatchRequest(filename: String, description: String, batchType: Symbol, tags: String, data: JValue)
