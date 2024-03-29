package com.ubirch.webui.services

import com.ubirch.webui.FeUtils
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.authentification.AuthenticationSupport
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.tenant.Tenant.GroupToTenant
import com.ubirch.webui.models.keycloak.tenant.{ Device, Tenant }
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil.RichUserRepresentation
import com.ubirch.webui.models.keycloak.util.Util

import com.typesafe.scalalogging.LazyLogging
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, Ok, ScalatraServlet }

import javax.ws.rs.NotFoundException
import scala.collection.JavaConverters._
import scala.util.Try

class ApiTenants(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging
  with AuthenticationSupport
  with ConfigBase {

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

  val getTenants: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getTenants")
      summary "Returns all the tenant hierarchy"
      description "Returns all the tenants hierarchy. All tenants returns with its own subtenants"
      tags "Tenants"
      parameters (
        swaggerTokenAsHeader,
        queryParam[String]("realm")
        .description("Keycloak realm to search tenants in")
        .defaultValue(keycloakRealm)
      ))

  val getDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getDevices")
      summary "Returns the devices of the given subtenant"
      description "Returns the devices of the given subtenant"
      tags "Tenants"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("tenantId")
        .description("Id of the subtenant"),
        queryParam[String]("realm")
        .description("Keycloak realm to search tenants in")
        .defaultValue(keycloakRealm),
        queryParam[Int]("page")
        .description("Pagination number")
        .defaultValue(0),
        queryParam[Int]("size")
        .description("Pagination size")
        .defaultValue(100)
      ))

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName)
    .description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")

  get("/", operation(getTenants)) {
    logger.debug(s"tenants: get(/tenants)")
    val realm = params.get("realm").getOrElse(keycloakRealm)

    whenLoggedInFromOtherSystem { _ =>
      val tenants = GroupFactory
        .getByName(rootTenantName)(realm)
        .representation
        .getSubGroups
        .asScala
        .map(_.groupRepresentationToTenant)
        .toList

      val tenantsWithSubTenants = tenants.map(createTenantTrees(_, realm)).sortBy(_.name)

      Ok(tenantsWithSubTenants)
    }

  }

  get("/:tenantId/devices", operation(getDevices)) {
    val tenantId = params("tenantId")
    val page = params.get("page").map(_.toInt).getOrElse(0)
    val size = params.get("size").map(_.toInt).getOrElse(100)
    val realm = params.get("realm").getOrElse(keycloakRealm)

    logger.debug(s"tenants: get(/tenants/$tenantId/devices) for realm:" + realm)

    whenLoggedInFromOtherSystem { _ =>
      val devices = Try(GroupFactory
        .getById(tenantId)(realm)
        .members(page * size, size)
        .asScala
        .toList
        .filter(_.toResourceRepresentation(realm).isDeviceSimple)
        .map(member =>
          Device(
            keycloakId = member.getId,
            deviceId = member.getUsername,
            description = member.getLastName,
            attributes = Util.attributesToMapFilteredAndSimple(member.getAttributes)
          )))
        .recover {
          case _: NotFoundException => Nil
        }.get

      Ok(devices)
    }

  }

  private def createTenantTrees(tenant: Tenant, realm: String): Tenant = {
    val subGroups = GroupFactory
      .getById(tenant.id)(realm)
      .toRepresentation
      .getSubGroups
      .asScala
      .map(_.groupRepresentationToTenant)
      .toList
      .sortBy(_.name)

    logger.info(s"tenant_name: ${tenant.name}, realm: $realm, subGroups: ${subGroups.map(_.name).mkString(", ")}")

    tenant.copy(subTenants = subGroups)
  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }
}
