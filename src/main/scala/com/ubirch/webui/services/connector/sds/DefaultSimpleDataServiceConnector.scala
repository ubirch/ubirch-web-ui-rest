package com.ubirch.webui.services.connector.sds

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

class DefaultSimpleDataServiceConnector extends SimpleDataServiceConnector {
  val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
}
