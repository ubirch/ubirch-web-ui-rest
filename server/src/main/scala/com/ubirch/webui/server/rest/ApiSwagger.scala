package com.ubirch.webui.server.rest

import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ ApiInfo, NativeSwaggerBase, Swagger }

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object RestApiInfo extends ApiInfo(
  "The Ubirch web admin API",
  "Docs for the Ubirch web admin API",
  "https://ubirch.de",
  "responsibleperson@ubirch.com",
  "Apache V2",
  "https://www.apache.org/licenses/LICENSE-2.0"
)

class ApiSwagger extends Swagger(Swagger.SpecVersion, "0.1.0", RestApiInfo)
