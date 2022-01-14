package com.ubirch.webui

import com.dimafeng.testcontainers.GenericContainer
import com.github.dockerjava.api.model.{ ExposedPort, HostConfig, PortBinding, Ports }
import com.ubirch.webui.KeycloakContainer.{ containerExposedPort, hostPort }
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

import java.time.Duration

class KeycloakContainer(underlying: GenericContainer, realmExportFile: String)
  extends GenericContainer(underlying) {
  underlying.container.withCopyFileToContainer(
    MountableFile.forHostPath(s"./$realmExportFile"),
    s"/tmp/$realmExportFile"
  )
  underlying.container.withCreateContainerCmdModifier(cmd =>
    cmd.withHostConfig(
      new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerExposedPort)))
    ))
}

object KeycloakContainer {
  val hostPort = 8080
  val containerExposedPort = 8080

  case class Def(realmExportFile: String)
    extends GenericContainer.Def[KeycloakContainer](
      new KeycloakContainer(
        GenericContainer(
          dockerImage = "quay.io/keycloak/keycloak:16.1.0",
          exposedPorts = List(containerExposedPort),
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

