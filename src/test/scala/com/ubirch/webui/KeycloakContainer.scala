package com.ubirch.webui

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

import java.time.Duration

class KeycloakContainer(underlying: GenericContainer, realmExportFile: String)
  extends GenericContainer(underlying) {
  underlying.container.withCopyFileToContainer(
    MountableFile.forHostPath(s"./$realmExportFile"),
    s"/tmp/$realmExportFile"
  )
}

object KeycloakContainer {
  val realmName: String = "test-realm"
  lazy val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = true, realmExportFile = "test-realm.json").start()

  case class Def(mountExtension: Boolean, realmExportFile: String)
    extends GenericContainer.Def[KeycloakContainer](
      new KeycloakContainer(
        GenericContainer(
          dockerImage = "quay.io/keycloak/keycloak:15.0.2",
          exposedPorts = List(8080),
          env = Map(
            "KEYCLOAK_USER" -> "admin",
            "KEYCLOAK_PASSWORD" -> "admin"
          ),
          command = List(
            "-c standalone.xml",
            "-b 0.0.0.0",
            "-Dkeycloak.profile.feature.upload_scripts=enabled",
            "-Dkeycloak.profile.feature.scripts=enabled",
            "-Dkeycloak.migration.action=import",
            "-Dkeycloak.migration.provider=singleFile",
            s"-Dkeycloak.migration.file=/tmp/$realmExportFile",
            "-Dkeycloak.migration.strategy=IGNORE_EXISTING"
          ),
          waitStrategy = Wait.forHttp("/auth").forPort(8080).withStartupTimeout(Duration.ofSeconds(120))
        ),
        realmExportFile
      )
    )

}

