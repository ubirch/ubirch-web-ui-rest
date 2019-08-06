package com.ubirch.webui.core

import cats.effect.{ContextShift, IO}
import com.ubirch.webui.core.structure.ClientRepresentation
import fs2.Stream
import io.circe.Json
import org.http4s.circe._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.io._
import org.http4s.{EntityDecoder, Method, Request, Uri, UrlForm}

import scala.concurrent.ExecutionContext.Implicits.global

object SimpleClient {
  private val instance = new SimpleClient
  def reqPost(url: String): Stream[IO, Json] = instance.get(url)
  def getAllClientsFromRealm(clientUri: Uri): Stream[IO, Json] = instance.getAllClientsFromRealm(clientUri)
  def reqPort(urlForm: UrlForm, url: Uri): Stream[IO, String] = instance.post(urlForm, url)
}

class SimpleClient {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  private val blazeClient: BlazeClientBuilder[IO] = BlazeClientBuilder[IO](global)

  def get(url: String): Stream[IO, Json] = {
    // Encode a User request
    val req = Method.GET(Uri.unsafeFromString(url))
    // Create a client
    blazeClient.stream.flatMap { httpClient =>
      // Decode a Hello response
      Stream.eval(httpClient.expect(req)(EntityDecoder[IO, Json]))
    }
  }


  def post(urlForm: UrlForm, url: Uri): Stream[IO, String] = {
    val req = Method.POST(urlForm, url)
    blazeClient.stream.flatMap { httpClient =>
      // Decode a Hello response
      Stream.eval(httpClient.expect(req)(EntityDecoder[IO, String]))
    }
  }

  def getAllClientsFromRealm(clientUrl: Uri): Stream[IO, Json] = {

    val req = Method.GET(clientUrl)
    blazeClient.stream.flatMap { httpClient =>
      // Decode a Hello response
      Stream.eval(httpClient.expect(req)(EntityDecoder[IO, Json]))
    }
  }
}
