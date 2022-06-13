package com.ubirch.webui.services

import com.ubirch.webui.FeUtils
import com.ubirch.webui.models.authentification.AuthenticationSupport
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.tenant.Tenant

import com.typesafe.scalalogging.LazyLogging
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, Ok, ScalatraServlet }

import scala.collection.JavaConverters._

class ApiTenants(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging with AuthenticationSupport {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers")
    )
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "Ubirch web admin keycloak group"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName)
    .description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")
  //.example(SwaggerDefaultValues.BEARER_TOKEN)

  get("/tenants") {
    logger.debug(s"tenants: get(/tenants)")
    val defaultRealm = "ubirch-default-realm"
    val TENANTS_UBIRCH = "TENANTS_UBIRCH"
    val realm = params.get("realm").getOrElse(defaultRealm)

    whenLoggedInFromOtherSystem { _ =>

      val tenants = GroupFactory
        .getByName(TENANTS_UBIRCH)(realm)
        .representation
        .getSubGroups
        .asScala
        .map(x => Tenant(x.getId, x.getName, x.getAttributes.asScala.toMap.mapValues(_.asScala.headOption.getOrElse("")), Nil))
        .toList

      Ok(tenants)
    }

  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }
}
