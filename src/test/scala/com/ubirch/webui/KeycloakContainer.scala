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
    MountableFile.forHostPath(s"$realmExportFile"),
    s"/opt/keycloak/data/import/realm.json"
  )
  underlying.container.withCreateContainerCmdModifier(cmd =>
    cmd.withHostConfig(
      new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerExposedPort))) // using fixed port
    ))
}

object KeycloakContainer {
  val hostPort = 8080
  val containerExposedPort = 8080

  case class Def(realmExportFile: String)
    extends GenericContainer.Def[KeycloakContainer](
      new KeycloakContainer(
        GenericContainer(
          dockerImage = "quay.io/keycloak/keycloak:20.0.5",
          exposedPorts = List(containerExposedPort),
          env = Map(
            "KEYCLOAK_ADMIN" -> "admin",
            "KEYCLOAK_ADMIN_PASSWORD" -> "admin"
          ),
          command = List(
            "start-dev",
            "--import-realm",
            "--http-relative-path=/auth"
          ),
          waitStrategy = Wait.forHttp("/auth").forPort(8080).withStartupTimeout(Duration.ofSeconds(120))
        ),
        realmExportFile
      )
    )

}

