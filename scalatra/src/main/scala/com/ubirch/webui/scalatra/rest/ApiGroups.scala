package com.ubirch.webui.scalatra.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.connector.TokenProcessor
import com.ubirch.webui.core.operations.{Groups, Utils}
import com.ubirch.webui.core.structure.{DeviceStubs, Group, User}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}
import org.scalatra.{CorsSupport, ScalatraServlet}

class ApiGroups(implicit val swagger: Swagger) extends ScalatraServlet
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

  def getToken: String = request.getHeader(tokenHeaderName)

  val tokenHeaderName = "Authorization"

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](tokenHeaderName).
    description("Token of the user")

  val getGroupsOfAUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[Group]]("getAllGroupOfUser")
      summary "Get all the groups of a user"
      description "see summary"
      tags "Groups"
      parameters swaggerTokenAsHeader)

  get("/getGroups", operation(getGroupsOfAUser)) {
    val tokenJWT: String = getToken
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.getGroupsOfAUser(Utils.getIdFromUserName(uInfo.userName))
  }

  val createGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("createGroup")
      summary "Create a group and add the user in it"
      description "see summary"
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupName").
        description("Name of the group")
    ))

  post("/createGroup/:groupName", operation(createGroup)) {
    val tokenJWT: String = getToken
    val groupName: String = params("groupName")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.createGroupAddUser(groupName, Utils.getIdFromUserName(uInfo.userName))
  }

  val getAllDevicesFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStubs]]("getAllDevicesFromGroup")
      summary "Get all the devices of a group"
      description "see summary"
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group")
    ))

  get("/getDevicesInGroup/:groupId", operation(getAllDevicesFromGroup)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.getMembersInGroup[DeviceStubs](groupId, "DEVICE", Utils.userRepresentationToDeviceStubs)
  }


  val getAllUsersFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[User]]("getAllUsersFromGroup")
      summary "Get all the users of a group"
      description "see summary"
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group")
    ))

  get("/getUsersInGroup/:groupId", operation(getAllUsersFromGroup)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.getMembersInGroup[User](groupId, "USER", Utils.userRepresentationToUser)
  }


  val addDeviceIntoGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addDeviceIntoGroup")
      summary "Add a device into a group"
      description "Add a device into a group. Can only be done if the user is the owner of the device"
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group"),
      queryParam[List[String]]("hwDeviceIds").
        description("HwDeviceIds of the device to be added on the group")
    ))

  get("/addDeviceIntoGroup/:groupId", operation(addDeviceIntoGroup)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val hwDeviceId: String = params.get("hwDeviceIds").get
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    val dId = hwDeviceId.split(",") map { hwId => Utils.getIdFromUserName(hwId) }
    Groups.addDevicesFromUserToGroup(Utils.getIdFromUserName(uInfo.userName), dId.toList, groupId)
  }

  val leaveGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("leaveGroup")
      summary "Make a user leave a group."
      description "Make a user leave a group."
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group")
    ))

  post("/leaveGroup/:groupId", operation(leaveGroup)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.leaveGroup(Utils.getIdFromUserName(uInfo.userName), groupId)
  }

  val deleteGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("deleteGroup")
      summary "Delete a group."
      description "Delete a group that the user controls."
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group")
    ))

  post("/deleteGroup/:groupId", operation(deleteGroup)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.deleteGroup(groupId)
  }

  val isGroupEmpty: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("isGroupEmpty")
      summary "Check if a group is empty."
      description "Check if a group is empty."
      tags "Groups"
      parameters(
      swaggerTokenAsHeader,
      pathParam[String]("groupId").
        description("Id of the group")
    ))

  get("/isGroupEmpty/:groupId", operation(isGroupEmpty)) {
    val tokenJWT: String = getToken
    val groupId: String = params("groupId")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Groups.isGroupEmpty(groupId)
  }
}
