package com.ubirch.webui.services

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.authentification.AuthenticationSupport
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.FeUtils
import com.ubirch.webui.models.keycloak.{ SimpleUser, UserAccountInfo }
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.{ CorsSupport, ScalatraServlet }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

class ApiUsers(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging with AuthenticationSupport {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS, PUT")
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"))
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "An example API"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName)
    .description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")
  //.example(SwaggerDefaultValues.BEARER_TOKEN)

  val getAccountInfo: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[UserAccountInfo]("getAccountInfo")
      summary "Get a user's basic info"
      description "Get a user's basic info: number of devices and last login"
      tags "Users"
      parameters swaggerTokenAsHeader)

  get("/accountInfo", operation(getAccountInfo)) {
    logger.info("users: get(/accountInfo)")
    whenLoggedInAsUserMemberResourceRepresentation { (userInfo, user) =>
      user.getAccountInfo(userInfo.realmName)
    }
  }

  val getUserFromUsername: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[SimpleUser]("getUserFromUsername")
      summary "Get a user from its username and realm"
      description "see summary"
      tags "Users"
      parameters (
        queryParam[String]("username")
        .description("Username of the user"),
        //.example(SwaggerDefaultValues.USERNAME),
        pathParam[String]("realmName")
        .description("Name of the realm where the user is")
      //.example(SwaggerDefaultValues.REALM_NAME)
      ))

  get("/getUserFromUsername/:realmName", operation(getUserFromUsername)) {
    implicit val realmName: String = params("realmName")
    val username: String = params.get("username").get
    logger.debug(s"the username is: $username")
    logger.debug(s"realm: $realmName")
    UserFactory.getByAName(username).toSimpleUser
  }

  val getUserFromToken: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[SimpleUser]("getUserFromToken")
      summary "Get a user from its Token"
      description "see summary"
      tags "Users"
      parameters swaggerTokenAsHeader)

  get("/", operation(getUserFromToken)) {
    logger.debug("user get(/)")
    whenLoggedInAsUser { (_, user) =>
      user.toSimpleUser
    }

  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }

}

