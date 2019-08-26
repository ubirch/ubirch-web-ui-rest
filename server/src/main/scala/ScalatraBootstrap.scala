import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.server.rest._
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle with ConfigBase {

  implicit val swagger: ApiSwagger = new ApiSwagger

  override def init(context: ServletContext) {

    context.initParameters("org.scalatra.cors.preflightMaxAge") = "5"

    context.initParameters("org.scalatra.cors.allowCredentials") = "false"

    // TODO: activate this once deploying on prod
    // cf http://scalatra.org/guides/2.6/deployment/configuration.html
    context.initParameters("org.scalatra.environment") = conf.getString("server.scalatra.environment")

    context.mount(new ApiUsers, "/users", "UserApi")
    context.mount(new ApiGroups, "/groups", "GroupApi")
    context.mount(new ApiDevices, "/devices", "DeviceApi")
    context.mount(new HealthCheck, "/checks", "HealthCheck")
    context.mount(new ResourcesApp, "/api-docs")
  }
}

