import com.ubirch.webui.scalatra.rest._
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger: ApiSwagger = new ApiSwagger

  override def init(context: ServletContext) {

    val baseUrl = "/ubirch-web-ui/api"
    val version = "/v1"

    context.initParameters("org.scalatra.cors.preflightMaxAge") = "1800"
    context.initParameters("org.scalatra.cors.allowCredentials") = "false"


    context.mount(new ApiUsers, baseUrl + version + "/users", "UserApi")
    context.mount(new ApiGroups, baseUrl + version + "/groups", "GroupApi")
    context.mount(new ApiDevices, baseUrl + version + "/devices", "DeviceApi")
    context.mount(new HealthCheck, baseUrl + version + "/checks", "HealthCheck")
    context.mount(new ResourcesApp, baseUrl + version + "/api-docs")
  }
}

