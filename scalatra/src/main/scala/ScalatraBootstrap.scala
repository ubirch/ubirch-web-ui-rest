import com.ubirch.webui.scalatra.rest.{ApiRest, ApiSwagger, ResourcesApp}
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger: ApiSwagger = new ApiSwagger

  override def init(context: ServletContext) {
    context.initParameters("org.scalatra.cors.allowedOrigins") = "http://0.0.0.0"
    context.mount(new ApiRest, "/id", "RestApi")
    context.mount(new ResourcesApp, "/api-docs")
  }
}

