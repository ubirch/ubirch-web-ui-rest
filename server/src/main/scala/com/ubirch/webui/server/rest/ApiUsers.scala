package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.operations.Users
import com.ubirch.webui.core.structure.User
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}
import org.scalatra.{CorsSupport, ScalatraServlet}

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

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName).
    description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")

  val getAccountInfo: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[AccountInfo]("getAccountInfo")
      summary "Get a user's basic info"
      description "Get a user's basic info: number of devices and last login"
      tags "Users"
      parameters swaggerTokenAsHeader)

  get("/accountInfo", operation(getAccountInfo)) {
    logger.info("users: get(/accountInfo)")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    val res = Users.getAccountInfo(uInfo.id)
    AccountInfo(res._1, res._2)
  }

  val getUserFromUsername: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[User]("getUserFromToken")
      summary "Get a user from its username and realm"
      description "see summary"
      tags "Users"
      parameters (
        queryParam[String]("username").
        description("Username of the user"),
        pathParam[String]("realmName").
        description("Name of the realm where the user is")
      ))

  get("/getUserFromUsername/:realmName", operation(getUserFromUsername)) {
    implicit val realmName: String = params("realmName")
    val username: String = params.get("username").get
    logger.debug(s"the username is: $username")
    logger.debug(s"realm: $realmName")
    Users.getUserByUsername(username)
  }

  val getUserFromToken: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[User]("getUserFromToken")
      summary "Get a user from its Token"
      description "see summary"
      tags "Users"
      parameters swaggerTokenAsHeader)

  get("/", operation(getUserFromToken)) {
    val uInfo = auth.get

    implicit val realmName: String = uInfo.realmName
    logger.debug(s"realm: $realmName")
    Users.getUserByUsername(uInfo.userName)
  }

}

case class AccountInfo(user: User = User("id", "JBKempf", "Kempf", "Jean-Baptiste"), numberDevices: Int = 23)
