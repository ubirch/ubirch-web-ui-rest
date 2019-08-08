package com.ubirch.webui.scalatra.rest

import com.typesafe.scalalogging.LazyLogging
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}
import org.scalatra.{CorsSupport, ScalatraServlet}

class ApiRest(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers")
    )
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "An example API"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  val bonjourMonde: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("helloWorld")
      summary "Says hello to the world"
      schemes("http", "https") // Force swagger ui to use http instead of https, only need to say it once TODO: change on prod !!
      description "Simple get command that returns: Salut à toi <name>>"
      tags "fff"
      parameters queryParam[Option[String]]("name").
      description("name of thing"))

  get("/bonjourMonde", operation(bonjourMonde)) {
    val name = params.getOrElse("name", "unknown guy")
    s"Salut à toi $name"
  }


}
