package com.ubirch.webui.services

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.authentification.AuthenticationSupport
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member.{ DeviceFactory, UserFactory }
import com.ubirch.webui.FeUtils
import com.ubirch.webui.models.keycloak.{ DeviceStub, GroupFE, SimpleUser }
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.util.MemberResourceRepresentation
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.{ CorsSupport, ScalatraServlet }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

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

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName)
    .description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")
  //.example(SwaggerDefaultValues.BEARER_TOKEN)

  val createGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("createGroup")
      summary "Create a group and add the user in it"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupName")
        .description("Name of the group")
      //.example(SwaggerDefaultValues.GROUP_NAME)
      ))

  post("/:groupName", operation(createGroup)) {
    logger.debug(s"groups: post(/)")
    whenLoggedInAsUserMemberResourceRepresentation { (userInfo, user) =>
      val groupName: String = params("groupName")
      implicit val realmName: String = userInfo.realmName
      val newGroup = GroupFactory.createGroup(groupName)
      user.joinGroup(newGroup)
    }
  }

  val getAllUsersFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[SimpleUser]]("getAllUsersFromGroup")
      summary "Get a maximum of 100 users of a group"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group")
      //.example(SwaggerDefaultValues.GROUP_ID)
      ))

  get("/:groupId/users/", operation(getAllUsersFromGroup)) {
    logger.debug(s"groups: get(/getUsersInGroup)")
    whenLoggedInAsUserNoFetch { userInfo =>
      val groupId: String = params("groupId")
      implicit val realmName: String = userInfo.realmName
      val group = GroupFactory.getById(groupId)
      group.getMembers.filter { u => u.toResourceRepresentation.isUser }.map { u => u.toSimpleUser }
    }
  }

  val addDeviceIntoGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addDeviceIntoGroup")
      summary "Add a device into a group"
      description "Add a device into a group. Can only be done if the user is the owner of the device"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group"),
        queryParam[List[String]]("hwDeviceIds")
        .description("HwDeviceIds of the devices to be added in the group")
      ))

  put("/:groupId/addDevice", operation(addDeviceIntoGroup)) {
    logger.debug(s"groups: put(/addDeviceIntoGroup)")
    whenLoggedInAsUserMemberResourceRepresentation { (userInfo, user) =>
      val groupId: String = params("groupId")
      val hwDeviceId: String = params.get("hwDeviceIds").get
      implicit val realmName: String = userInfo.realmName
      val devicesId = hwDeviceId.split(",")
      val devices = devicesId.map { dId => DeviceFactory.getByHwDeviceId(dId).right.get.toResourceRepresentation }.toList
      val group = GroupFactory.getById(groupId)
      user.addDevicesToGroup(devices, group.toRepresentation)
    }
  }

  val leaveGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("leaveGroup")
      summary "Make a user leave a group."
      description "Make a user leave a group."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group")
      ))

  post("/:groupId/leave", operation(leaveGroup)) {
    logger.debug(s"groups: post(/leave)")
    whenLoggedInAsUserMemberResourceRepresentation { (userInfo, user) =>
      val groupId: String = params("groupId")
      implicit val realmName: String = userInfo.realmName
      logger.debug(s"realm: $realmName")
      user.leaveGroup(GroupFactory.getById(groupId).toRepresentation)
    }

  }

  val deleteGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("deleteGroup")
      summary "Delete a group."
      description "Delete a group that the user controls."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group")
      ))

  delete("/:groupId", operation(deleteGroup)) {
    logger.debug(s"group delete")
    whenLoggedInAsUserNoFetch { userInfo =>
      val groupId: String = params("groupId")
      implicit val realmName: String = userInfo.realmName
      val group = GroupFactory.getById(groupId).toResourceRepresentation
      group.deleteGroup()
    }
  }

  val isGroupEmpty: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("isGroupEmpty")
      summary "Check if a group is empty."
      description "Check if a group is empty."
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group")
      ))

  get("/:groupId/isEmpty", operation(isGroupEmpty)) {
    val groupId: String = params("groupId")
    logger.debug(s"group get(/isEmpty:$groupId)")
    whenLoggedInAsUserNoFetch { userInfo =>
      implicit val realmName: String = userInfo.realmName
      val group = GroupFactory.getById(groupId)
      group.isEmpty
    }

  }

  val getAllDevicesFromGroup: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStub]]("getAllDevicesFromGroup")
      summary "Get a maximum of 100 devices of a group"
      description "see summary"
      tags "Groups"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("groupId")
        .description("Id of the group")
      //.example(SwaggerDefaultValues.GROUP_ID)
      ))

  get("/:groupId/devices", operation(getAllDevicesFromGroup)) {
    logger.debug(s"groups: get()")
    whenLoggedInAsUserNoFetch { userInfo =>
      val groupId: String = params("groupId")
      implicit val realmName: String = userInfo.realmName
      val group = GroupFactory.getById(groupId)
      group.getMembers.map(_.toResourceRepresentation).filter(_.isDevice) map { d => d.toDeviceStub }
    }
  }

  val getGroupsOfAUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[GroupFE]]("getAllGroupOfUser")
      summary "Get all the groups of a user"
      description "see summary"
      tags "Groups"
      parameters swaggerTokenAsHeader)

  get("/", operation(getGroupsOfAUser)) {
    whenLoggedInAsUserMemberResourceRepresentation { (userInfo, user: MemberResourceRepresentation) =>
      implicit val realmName: String = userInfo.realmName
      logger.debug(s"realm: $realmName")
      user.getGroupsFiltered map { g => g.toGroupFE }
    }
  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getMessage.getClass.toString, e.getMessage))
  }
}
