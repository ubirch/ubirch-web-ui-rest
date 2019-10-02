package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.operations.{Groups, Utils}
import com.ubirch.webui.core.structure.{DeviceStubs, Group, User}
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{CorsSupport, ScalatraServlet}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}

class ApiGroups(implicit val swagger: Swagger) extends ScalatraServlet
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

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName).
    description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")

  val createGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("createGroup")
      summary "Create a group and add the user in it"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        queryParam[String]("groupName").
        description("Name of the group")
      ))

  post("/:groupName", operation(createGroup)) {
    logger.debug(s"groups: post(/)")
    val userInfo = auth.get
    val groupName: String = params("groupName")
    implicit val realmName: String = userInfo.realmName
    Groups.createGroupAddUser(groupName, Utils.getIdFromUserName(userInfo.userName))
  }

  val getAllUsersFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[User]]("getAllUsersFromGroup")
      summary "Get all the users of a group"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group")
      ))

  get("/:groupId/users/", operation(getAllUsersFromGroup)) {
    logger.debug(s"groups: get(/getUsersInGroup)")
    val userInfo = auth.get
    val groupId: String = params("groupId")
    implicit val realmName: String = userInfo.realmName
    Groups.getMembersInGroup[User](groupId, "USER", Utils.userRepresentationToUser)
  }

  val addDeviceIntoGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addDeviceIntoGroup")
      summary "Add a device into a group"
      description "Add a device into a group. Can only be done if the user is the owner of the device"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group"),
        queryParam[List[String]]("hwDeviceIds").
        description("HwDeviceIds of the device to be added on the group")
      ))

  put("/:groupId/addDevice", operation(addDeviceIntoGroup)) {
    logger.debug(s"groups: put(/addDeviceIntoGroup)")
    val userInfo = auth.get
    val groupId: String = params("groupId")
    val hwDeviceId: String = params.get("hwDeviceIds").get
    implicit val realmName: String = userInfo.realmName
    val dId = hwDeviceId.split(",") map { hwId => Utils.getIdFromUserName(hwId) }
    Groups.addDevicesFromUserToGroup(Utils.getIdFromUserName(userInfo.userName), dId.toList, groupId)
  }

  val leaveGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("leaveGroup")
      summary "Make a user leave a group."
      description "Make a user leave a group."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group")
      ))

  post("/:groupId/leave", operation(leaveGroup)) {
    logger.debug(s"groups: post(/leave)")
    val userInfo = auth.get
    val groupId: String = params("groupId")
    implicit val realmName: String = userInfo.realmName
    logger.debug(s"realm: $realmName")
    Groups.leaveGroup(Utils.getIdFromUserName(userInfo.userName), groupId)
  }

  val deleteGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("deleteGroup")
      summary "Delete a group."
      description "Delete a group that the user controls."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group")
      ))

  delete("/:groupId", operation(deleteGroup)) {
    logger.debug(s"group  delete")
    val groupId: String = params("groupId")
    val userInfo = auth.get
    implicit val realmName: String = userInfo.realmName
    Groups.deleteGroup(groupId)
  }

  val isGroupEmpty: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("isGroupEmpty")
      summary "Check if a group is empty."
      description "Check if a group is empty."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group")
      ))

  get("/:groupId/isEmpty", operation(isGroupEmpty)) {
    val groupId: String = params("groupId")
    logger.debug(s"group get(/isEmpty:$groupId)")
    val userInfo = auth.get
    implicit val realmName: String = userInfo.realmName
    Groups.isGroupEmpty(groupId)
  }

  val getAllDevicesFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStubs]]("getAllDevicesFromGroup")
      summary "Get all the devices of a group"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId").
        description("Id of the group")
      ))

  get("/:groupId/devices", operation(getAllDevicesFromGroup)) {
    logger.debug(s"groups: get()")
    val userInfo = auth.get
    val groupId: String = params("groupId")
    implicit val realmName: String = userInfo.realmName
    Groups.getMembersInGroup[DeviceStubs](groupId, "DEVICE", Utils.userRepresentationToDeviceStubs)
  }

  val getGroupsOfAUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[Group]]("getAllGroupOfUser")
      summary "Get all the groups of a user"
      description "see summary"
      tags "Groups"
      parameters swaggerTokenAsHeader)

  get("/", operation(getGroupsOfAUser)) {
    val userInfo = auth.get
    implicit val realmName: String = userInfo.realmName
    logger.debug(s"realm: $realmName")
    Groups.getGroupsOfAUser(userInfo.id)
  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }
}
