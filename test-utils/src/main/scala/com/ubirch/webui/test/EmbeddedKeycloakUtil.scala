package com.ubirch.webui.test

import java.io.File
import java.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicNameValuePair
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.keycloak.adapters.HttpClientBuilder
import org.tmt.embedded_keycloak.{ EmbeddedKeycloak, KeycloakData, Settings }
import os.proc

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.{ BufferedSource, Source }

trait EmbeddedKeycloakUtil extends Elements with LazyLogging { //extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Elements {

  val keyCloakSettings = Settings(
    port = 8080,
    host = "0.0.0.0",
    keycloakDirectory = getBasePath + "/embedded-keycloak/",
    cleanPreviousData = true,
    alwaysDownload = false,
    version = "6.0.1"
  )

  private def getBasePath: String = {
    val res = System.getProperty("user.home")
    val path = if (res.equals("?") || res.isEmpty) {
      "/tmp"
    } else res
    logger.info("path: " + path)
    path
  }

  val keycloak = new EmbeddedKeycloak(
    KeycloakData(), //ConfigFactory.load("resources/application.conf").getConfig("embedded-keycloak").as[KeycloakData], // or directly: `KeycloakData(...)`
    keyCloakSettings
  ) // or customize: Settings(...)

  implicit val realmName: String = "test-realm"

  val keyCloakPort = 8080

  val realmFile = new File("test-realm.json")
  logger.info(realmFile.getAbsolutePath)
  if (!realmFile.exists()) throw new Exception("No test realm data found")

  startKcIfNotStarted(realmFile)

  private def startKcIfNotStarted(realmFile: File): Unit = {
    val isKcStarted = try {
      getKeyCloakPid(keyCloakPort)
      logger.info("KeyCloak already started on device")
      true
    } catch {
      case _: Throwable => false
    }
    if (!isKcStarted) {
      Await.result(keycloak.startServer(), 5.minutes)

      // logger.info(s"system.getProp(user.dir): ${System.getProperty("user.dir")}")
      // val realmJsonConfigFilename = System.getProperty("user.dir") + "/src/test/resources/`test-realm.json`"
      importTestRealm(realmFile)
    }
  }

  /*
  Workaround as integrated shutdown doesn't seem to work
 */
  def stopEmbeddedKeycloak(): Unit = {
    logger.info("Bye bye embedded keycloak")
    val embeddedKcPid = getKeyCloakPid(keyCloakPort)
    proc("kill", "-9", embeddedKcPid).call()
  }

  private def getKeyCloakPid(port: Int) = {
    proc("lsof", "-t", "-i", s":$port", "-s", "TCP:LISTEN").call().chunks.iterator
      .collect {
        case Left(s) => s
        case Right(s) => s
      }
      .map(x => new String(x.array)).map(_.trim.toInt).toList.head
  }

  /**
    * Get a keycloak admin access token (duration: 1 second)
    */
  def getAdminToken: String = {
    val url = s"http://localhost:$keyCloakPort/auth/realms/master/protocol/openid-connect/token"
    val post = new HttpPost(url)
    val client = new HttpClientBuilder().build()

    val nameValuePairs = new util.ArrayList[NameValuePair](1)
    nameValuePairs.add(new BasicNameValuePair("grant_type", "password"))
    nameValuePairs.add(new BasicNameValuePair("username", "admin"))
    nameValuePairs.add(new BasicNameValuePair("password", "admin"))
    nameValuePairs.add(new BasicNameValuePair("client_id", "admin-cli"))
    post.setEntity(new UrlEncodedFormEntity(nameValuePairs))

    // send the post request
    val response = client.execute(post)

    val responseBody = parse(scala.io.Source.fromInputStream(response.getEntity.getContent).mkString)
    implicit val formats: DefaultFormats.type = DefaultFormats

    val adminAccessToken = (responseBody \ "access_token").extract[String]
    adminAccessToken
  }

  def importTestRealm(realmFile: File): Unit = {
    Thread.sleep(1000)
    logger.info("import test-realm")
    var postRequestBody: BufferedSource = null
    try {
      postRequestBody = Source.fromFile(realmFile)
      // send post request realm
      val url = s"http://localhost:$keyCloakPort/auth/admin/realms"
      val post = new HttpPost(url)
      post.addHeader("Content-Type", "application/json")
      post.addHeader("Authorization", s"bearer $getAdminToken")
      val client = new HttpClientBuilder().build()
      post.setEntity(new StringEntity(postRequestBody.getLines.toList.mkString("")))
      client.execute(post)

      addEcdsaKeyToTestRealm()

    } catch {
      case e: Exception =>
        stopEmbeddedKeycloak()
        throw e
    } finally {
      if (postRequestBody != null) postRequestBody.close()
    }
  }

  def addEcdsaKeyToTestRealm(): Unit = {
    logger.info("importing ecdsa key")
    val reqBody = """{"name":"ecdsa-generated","providerId":"ecdsa-generated","providerType":"org.keycloak.keys.KeyProvider","parentId":"test-realm","config":{"priority":["0"],"enabled":["true"],"active":["true"],"ecdsaEllipticCurveKey":["P-256"]}}"""
    val url = s"http://localhost:$keyCloakPort/auth/admin/realms/test-realm/components"
    val post = new HttpPost(url)
    post.addHeader("Content-Type", "application/json")
    post.addHeader("Authorization", s"bearer $getAdminToken")
    val client = new HttpClientBuilder().build()
    post.setEntity(new StringEntity(reqBody))
    client.execute(post)
  }

}
