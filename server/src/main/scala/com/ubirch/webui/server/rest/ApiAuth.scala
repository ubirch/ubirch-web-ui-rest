package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.operations.{Auth, Devices}
import com.ubirch.webui.core.structure.Device
import com.ubirch.webui.core.Exceptions.NotAuthorized
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import com.ubirch.webui.server.Models.SwaggerResponse
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{CorsSupport, Ok, ScalatraServlet, Unauthorized}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}

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

  before() {
  }

  val authDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("Authenticate")
      summary "Authenticate"
      description "Authenticate a device"
      schemes "http"
      tags "Auth"
      parameters (
        headerParam[String]("X-Ubirch-Hardware-Id").
        description("HardwareId of the device"),
        headerParam[String]("X-Ubirch-Credential").
        description("Password of the device")
      )
        responseMessage SwaggerResponse.UNAUTHORIZED)
  //SwaggerResponse.AUTHORIZED))

  get("/", operation(authDevice)) {
    contentType = formats("txt")
    val hwDeviceId = request.header("X-Ubirch-Hardware-Id").getOrElse("")
    val password = request.header("X-Ubirch-Credential").getOrElse("null")
    logger.debug(s"authDevice: get(/$hwDeviceId), password= $password")
    val res = try {
      Auth.auth(hwDeviceId, password)
    } catch {
      case notAuthorizedException: NotAuthorized =>
        logger.debug(notAuthorizedException.getMessage)
        Unauthorized(notAuthorizedException.getMessage)
    }
    Ok(res)
  }

  val deviceInfo: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Device]("getDeviceInfo")
      summary "Get the info of a device"
      description "Get the info of a device"
      schemes "http"
      tags "Auth"
      // responseMessage SwaggerResponse.DEVICE
      parameters headerParam[String](FeUtils.tokenHeaderName).
      description("Token of the device. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK"))

  get("/deviceInfo", operation(deviceInfo)) {
    contentType = formats("json")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    Devices.getDeviceByInternalKcId(uInfo.id)
  }

}
