package com.ubirch.webui.services

import com.typesafe.scalalogging.LazyLogging
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, Ok, ScalatraServlet }

class HealthCheck(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers")
    )
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "Check if the service is running correctly."

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  val check: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("check")
      summary "Simple check."
      description "Simple check to see if the service is up"
      tags "Check")

  get("/check", operation(check)) {
    Ok("alive")
  }

}
