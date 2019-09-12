package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.operations.Auth
import com.ubirch.webui.core.structure.Device
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.{ CorsSupport, Ok, ScalatraServlet }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

class ApiAuth(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging with AuthenticationSupport
  with ConfigBase {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS, PUT")
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"))
    //response.setHeader("Access-Control-Allow-Origin", "*")
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "Ubirch auth API"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format and increase prometheus counter.
  before() {
    contentType = formats("json")
  }

  val authDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Device]("getOneDevice")
      summary "Get one device"
      description "Get one device belonging to a user from his hwDeviceId"
      schemes "http"
      tags "Auth"
      parameters (
        headerParam[String]("X-Ubirch-Hardware-Id").
        description("HardwareId of the device"),
        headerParam[String]("X-Ubirch-Credential").
        description("password of the device")
      ))

  get("/", operation(authDevice)) {
    val hwDeviceId = request.header("X-Ubirch-Hardware-Id").getOrElse("")
    val password = request.header("X-Ubirch-Credential").getOrElse("null")
    logger.debug(s"authDevice: get(/$hwDeviceId), password= $password")
    try {
      Auth.auth(hwDeviceId, password)
    } catch {
      case e: Throwable =>
        logger.debug(e.getMessage)
        halt(401, e)
    }
    Ok()
  }

}
