package com.ubirch.webui.scalatra.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.connector.TokenProcessor
import com.ubirch.webui.core.operations.Users
import com.ubirch.webui.core.structure.User
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}
import org.scalatra.{CorsSupport, ScalatraServlet}

class ApiUsers(implicit val swagger: Swagger) extends ScalatraServlet
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

  val getUserFromToken: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[User]("getUserFromToken")
      summary "Get a user from its Token"
      description "see summary"
      tags "Users"
      parameters queryParam[String]("token").
      description("Token of the user"))

  get("/getUserFromToken", operation(getUserFromToken)) {
    val tokenJWT: String = params.get("token").get
    println(s"the token is: $tokenJWT")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Users.findUserByUsername(uInfo.userName)
  }

  val getUserFromUsername: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[User]("getUserFromToken")
      summary "Get a user from its username and realm"
      description "see summary"
      tags "Users"
      parameters(
      queryParam[String]("username").
        description("Username of the user"),
      pathParam[String]("realmName").
        description("Name of the realm where the user is")
    ))

  get("/getUserFromUsername/:realmName", operation(getUserFromUsername)) {
    implicit val realmName: String = params("realmName")
    val username: String = params.get("username").get
    println(s"the username is: $username")
    logger.info(s"realm: $realmName")
    Users.findUserByUsername(username)
  }


}
