package com.ubirch.webui.scalatra.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.connector.TokenProcessor
import com.ubirch.webui.core.operations.{Devices, Users}
import com.ubirch.webui.core.structure.{AddDevice, Device, DeviceStubs, User}
import com.ubirch.webui.scalatra.FeUtils
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}
import org.scalatra.{CorsSupport, ScalatraServlet}


class ApiDevices(implicit val swagger: Swagger) extends ScalatraServlet
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

  val createDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("createDevice")
      summary "Create de device"
      description "Allows a user to create de device"
      tags "Devices"
      parameters(
      queryParam[String]("token").
        description("Token of the user"),
      queryParam[String]("hwDeviceId").
        description("The id of the device"),
      queryParam[String]("deviceType").
        description("The type of the device"),
      queryParam[Option[String]]("description").
        description("OPTIONAL - A description of the device"),
      queryParam[Option[List[String]]]("groupList").
        description("OPTIONAL - List of groups Id that the device should join")
    ))

  post("/createDevice", operation(createDevice)) {
    val tokenJWT: String = params.get("token").get
    val hwDeviceId = params.get("hwDeviceId").get
    val deviceType = params.get("deviceType").get
    val description: String = params.get("description").getOrElse(hwDeviceId)
    val listGroups: List[String] = FeUtils.extractListOfSFromString(params.getOrElse("groupList", ""))
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    val user: User = Users.getUserByUsername(uInfo.userName)
    logger.info(s"realm: $realmName")
    Devices.createDevice(user.id, AddDevice(hwDeviceId, description, deviceType, listGroups))
  }

  val getAllDevicesFromUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStubs]]("getUserFromToken")
      summary "List all the devices of one user"
      description "For the moment does not support pagination"
      tags "Devices"
      parameters queryParam[String]("token").
      description("Token of the user"))

  get("/getDevicesUser", operation(getAllDevicesFromUser)) {
    val tokenJWT: String = params.get("token").get
    println(s"the token is: $tokenJWT")
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Users.listAllDevicesStubsOfAUser(0, 0, uInfo.userName)
  }

  val getOneDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Device]("getOneDevice")
      summary "Get one device"
      description "Get one device belonging to a user from his hwDeviceId"
      tags "Devices"
      parameters(
      queryParam[String]("token").
        description("Token of the user"),
      queryParam[String]("hwDeviceId").
        description("hwDeviceId of the device")
    ))

  get("/getSingleDevice", operation(getOneDevice)) {
    val tokenJWT: String = params.get("token").get
    val hwDeviceId = params.get("hwDeviceId").get
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Devices.getSingleDeviceFromUser(hwDeviceId, uInfo.userName)
  }

  val deleteDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("deleteOneDevice")
      summary "Delete a single device"
      description "Delete one device belonging to a user from his hwDeviceId"
      tags "Devices"
      parameters(
      queryParam[String]("token").
        description("Token of the user"),
      queryParam[String]("hwDeviceId").
        description("hwDeviceId of the device that will be deleted")
    ))

  post("/deleteSingleDevice", operation(deleteDevice)) {
    val tokenJWT: String = params.get("token").get
    val hwDeviceId = params.get("hwDeviceId").get
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    Devices.deleteDevice(uInfo.userName, hwDeviceId)
  }

  val addBulkDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addBulkDevices")
      summary "Add multiple devices."
      description "Add multiple devices."
      tags "Devices"
      parameters(
      queryParam[String]("token").
        description("Token of the user"),
      queryParam[List[AddDevice]]("listDevices").
        description("List of device representation to add [{hwDeviceId: String, description: String, deviceType: String, listGroups: List[String]}].")
    ))

  post("/addBulkDevices", operation(addBulkDevices)) {
    val tokenJWT: String = params.get("token").get
    val lDevicesString: String = params.get("listDevices").get
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    logger.info(s"lDevicesString = $lDevicesString")
    val user: User = Users.getUserByUsername(uInfo.userName)
    val lDevices = read[List[AddDevice]](lDevicesString)
    println(lDevices)
    val res = Devices.bulkCreateDevice(user.id, lDevices)
    s"[${res.mkString(",")}]"
  }

  val updateDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("updateDevice")
      summary "Update a device."
      description "Update a device. The specified device will see all its attributes replaced by the new provided one."
      tags "Devices"
      parameters(
      queryParam[String]("token").
        description("Token of the user"),
      pathParam[String]("hwDeviceId").
        description("hwDeviceId of the device"),
      queryParam[String]("ownerId").
        description("KeyCloak id of the owner of the device"),
      queryParam[String]("deviceConfig").
        description("JSON formatted device config of the device"),
      queryParam[String]("apiConfig").
        description("KeyCloak id of the owner of the device"),
      queryParam[String]("description").
        description("Description of the device"),
      queryParam[String]("type").
        description("Type of the device. Can only be an existing one"),
      queryParam[List[String]]("listGroups").
        description("List of the groups the device belongs to")
    ))

  post("/updateDevice/:hwDeviceId", operation(updateDevice)) {
    val tokenJWT: String = params.get("token").get
    val hwDeviceId: String = params("hwDeviceId")
    val ownerId: String = params.get("ownerId").get
    val apiConfig: String = params.get("apiConfig").get
    val deviceConfig: String = params.get("deviceConfig").get
    val description: String = params.get("description").get
    val deviceType: String = params.get("type").get
    val groupList: List[String] = FeUtils.extractListOfSFromString(params.get("listGroups").get)
    val token = TokenProcessor.stringToToken(tokenJWT)
    val uInfo = TokenProcessor.getUserInfo(token)
    implicit val realmName: String = uInfo.realmName
    logger.info(s"realm: $realmName")
    val user: User = Users.getUserByUsername(uInfo.userName)
    val addDevice = AddDevice(hwDeviceId, description, deviceType, groupList)
    Devices.updateDevice(ownerId, addDevice, deviceConfig, apiConfig)
  }


}



