package com.ubirch.webui.core

import java.util.concurrent._

import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.http4s.circe._
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.http4s.headers.{Accept, Authorization, `Content-Type`}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, Method, Request, Uri, UrlForm}
import org.scalatest.{FeatureSpec, Matchers}
import org.http4s.client.blaze._
import org.http4s.client._
import org.http4s.MediaType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}


class Test extends FeatureSpec with LazyLogging with Matchers {

/*  val httpClient = BlazeClientBuilder[IO](global).resource.use { client =>
    // use `client` here and return an `IO`.
    // the client will be acquired and shut down
    // automatically each time the `IO` is run.
    IO.unit
  }*/
  val httpClient: SimpleClient.type = SimpleClient


  feature("GET requests") {

    scenario(".well-known") {

      val url = "http://localhost:8080/auth/realms/master/.well-known/openid-configuration"

      logger.info(httpClient.reqPost(url).compile.last.unsafeRunSync().toString)
      logger.info(httpClient.reqPost(url).compile.last.unsafeRunSync().toString)

    }

    scenario("clients") {
      val uri = Uri.unsafeFromString("http://localhost:8080/auth/master/clients")

      logger.info(httpClient.getAllClientsFromRealm(uri).compile.last.unsafeRunSync().toString)

    }
  }


}
