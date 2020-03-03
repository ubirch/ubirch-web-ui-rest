package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.{ HexDecodingError, NotAuthorized }
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure.member.DeviceFactory
import com.ubirch.webui.core.structure.{ Auth, DeviceFE }
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import com.ubirch.webui.server.models.SwaggerResponse
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, InternalServerError, Ok, ScalatraServlet }

class ApiAuth(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging with AuthenticationSupport
  with ConfigBase {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS, PUT")
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"))
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "Ubirch auth API"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  before() {
  }

  val authDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("Authenticate")
      summary "Authenticate"
      description "Authenticate a device against Ubirch device management system. Returns a token if valid, HTTP error code 401 if not"
      schemes ("http", "https")
      tags "Auth"
      parameters (
        headerParam[String]("X-Ubirch-Hardware-Id").
        description("HardwareId of the device"),
        headerParam[String]("X-Ubirch-Credential").
        description("Password of the device, base64 encoded")
      )
        responseMessage SwaggerResponse.UNAUTHORIZED)

  get("/", operation(authDevice)) {
    contentType = formats("txt")
    val hwDeviceId = request.header("X-Ubirch-Hardware-Id").getOrElse("")
    val password = request.header("X-Ubirch-Credential").getOrElse("null")
    logger.debug(s"authDevice: get(/$hwDeviceId)")
    val res = try {
      Auth.auth(hwDeviceId, password)
    } catch {
      case e: NotAuthorized =>
        logger.warn("Device not authorized: " + e.getMessage)
        halt(401, FeUtils.createServerError("Authentication", e.getMessage))
      case e: HexDecodingError =>
        halt(400, FeUtils.createServerError("Invalid base64 value for password", e.getMessage))
      case e: Throwable =>
        logger.error(FeUtils.createServerError(e.getClass.toString, e.getMessage))
        InternalServerError(FeUtils.createServerError(e.getClass.toString, e.getMessage))
    }
    Ok(res)
  }

  val deviceInfo: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[DeviceFE]("getDeviceInfo")
      summary "Get the info of a device"
      description "Get general information about a device."
      schemes "http"
      tags "Auth"
      // responseMessage SwaggerResponse.DEVICE
      parameters headerParam[String](FeUtils.tokenHeaderName).
      description("""Token of the device. The token can be obtained from the /auth endpoint. Should follow the syntax "bearer TOKEN"""))

  get("/deviceInfo", operation(deviceInfo)) {
    contentType = formats("json")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    DeviceFactory.getByHwDeviceId(uInfo.userName).toDeviceFE
  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }
}
