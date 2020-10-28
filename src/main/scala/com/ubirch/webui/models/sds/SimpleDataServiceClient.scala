package com.ubirch.webui.models.sds

import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.Elements
import com.ubirch.webui.services.connector.sds.SimpleDataServiceConnector
import sttp.client.{ basicRequest, SttpBackend }
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client._

import scala.concurrent.{ ExecutionContext, Future }

trait SimpleDataServiceClient {

  /**
    * Return the last _number_ values stored in the sds by the thing having the device id _hwDeviceId_
    * Auth by hwDeviceId and password
    * Use ascii password, the method will convert it to base64 by itself
    */
  def getLastValues(hwDeviceId: String, password: String, number: Int): Future[String]

}

class DefaultDataServiceClient(sdsHttpClient: SimpleDataServiceConnector) extends SimpleDataServiceClient with LazyLogging with ConfigBase {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = sdsHttpClient.sttpBackend

  def getLastValues(hwDeviceId: String, password: String, number: Int): Future[String] = {
    val trimmedNumber = if (number > 100) 100 else number
    val path = "lastValues/" + trimmedNumber
    val url = sdsBaseUrl + path
    val b64Password = Base64.getEncoder.encodeToString(password.getBytes())
    val request = basicRequest.get(uri"$url")
      .header(Elements.UBIRCH_SDS_HEADER_ID, hwDeviceId)
      .header(Elements.UBIRCH_SDS_HEADER_PASSWORD, b64Password)
    val fRes = request.send()
    fRes.map { res =>
      if (res.isSuccess) {
        res.body match {
          case Left(_) =>
            logger.error(s"SDS QUERY: Error even though query was successful for device $hwDeviceId")
            "SDS QUERY: Error even though query was successful"
          case Right(value) =>
            value
        }
      } else {
        res.body.getOrElse("Error: " + res.code.toString())
      }
    }
  }

}
