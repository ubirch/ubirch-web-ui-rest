package com.ubirch.webui.services.connector.sds

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.SttpBackend

import scala.concurrent.Future

trait SimpleDataServiceConnector {
  val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler]
}
