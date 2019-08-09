import com.ubirch.webui.scalatra.rest._
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger: ApiSwagger = new ApiSwagger

  override def init(context: ServletContext) {
    context.initParameters("org.scalatra.cors.allowedOrigins") = "http://0.0.0.0"
    context.mount(new ApiUsers, "/users", "UserApi")
    context.mount(new ApiGroups, "/groups", "GroupApi")
    context.mount(new ApiDevices, "/devices", "DeviceApi")
    context.mount(new ResourcesApp, "/api-docs")
  }
}

