package com.ubirch.webui

import java.io.{ File, IOException }
import com.typesafe.scalalogging.LazyLogging

import java.net.ServerSocket

trait KeycloakTestContainerUtil extends Elements with LazyLogging { //extends FeatureSpec with LazyLogging with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Elements {

  implicit val realmName: String = "test-realm"

  val realmFile = new File(KeycloakContainers.realmFilePath)
  logger.info(realmFile.getAbsolutePath)
  if (!realmFile.exists()) throw new Exception("No test realm data found")

  startKcIfNotStarted()

  private def startKcIfNotStarted(): Unit = {
    val isKcStarted = isPortInUse(KeycloakContainer.hostPort)
    if (!isKcStarted) {
      logger.info("test container starts running")
      KeycloakContainers.container
    } else {
      logger.info("KeyCloak already started on device")
    }
  }

  def isPortInUse(port: Int): Boolean = {
    var ss: ServerSocket = null
    try {
      ss = new ServerSocket(port)
      ss.setReuseAddress(true)
      return false
    } catch {
      case _: IOException =>

    } finally {
      if (ss != null)
        try ss.close()
        catch {
          case _: IOException =>
        }
    }
    true
  }
}

object KeycloakContainers {
  val realmFilePath = "test-realm.json"

  lazy val container = KeycloakContainer.Def(realmFilePath).start()
}
