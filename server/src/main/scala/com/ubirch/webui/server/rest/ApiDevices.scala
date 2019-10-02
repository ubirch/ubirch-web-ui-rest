package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.operations.{Devices, Users}
import com.ubirch.webui.core.structure._
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import com.ubirch.webui.server.models.UpdateDevice
import org.json4s.{DefaultFormats, Formats, _}
import org.json4s.jackson.Serialization.{read, write}
import org.scalatra.{CorsSupport, Ok, ScalatraServlet}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}

class ApiDevices(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport with LazyLogging with AuthenticationSupport
  with ConfigBase {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS, PUT")
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"))
  }

  // Stops the APIJanusController from being abstract
  protected val applicationDescription = "An example API"

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format and increase prometheus counter.
  before() {
    contentType = formats("json")
  }

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName).
    description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")

  val getOneDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Device]("getOneDevice")
      summary "Get one device"
      description "Get one device belonging to a user from his hwDeviceId"
      schemes "http"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("id").
        description("hwDeviceId of the device")
      ))

  get("/:id", operation(getOneDevice)) {
    logger.info("devices: get(/:id)")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    val hwDeviceId = getHwDeviceId
    Devices.getSingleDeviceFromUser(hwDeviceId, uInfo.userName)
  }

  val searchForDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[Device]]("searchForDevices")
      summary "Search for devices matching a specific attribute"
      description "Search for devices matching a specific attribute: description, hwDeviceId, ..."
      schemes "http"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("search").
        description("String that will be used for the search")
      ))

  get("/search/:search", operation(searchForDevices)) {
    logger.info("devices: get(/search/:search)")
    val uInfo = auth.get
    val search = params("search")
    implicit val realmName: String = uInfo.realmName
    Devices.searchMultipleDevices(search, uInfo.userName)
  }

  val deleteDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("deleteOneDevice")
      summary "Delete a single device"
      description "Delete one device belonging to a user from his hwDeviceId"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("id").
        description("hwDeviceId of the device that will be deleted")
      ))

  delete("/:id", operation(deleteDevice)) {
    logger.debug("devices: delete(/:id)")
    val hwDeviceId = getHwDeviceId
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    Devices.deleteDevice(uInfo.userName, hwDeviceId)
  }

  val addBulkDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addBulkDevices")
      summary "Add multiple devices."
      description "Add multiple devices."
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        bodyParam[List[AddDevice]]("listDevices").
        description("List of device representation to add [{hwDeviceId: String, description: String, deviceType: String, listGroups: List[String]}].")
      ))

  post("/", operation(addBulkDevices)) {
    logger.debug("devices: post(/)")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    val devicesAsString: String = request.body
    val user: User = Users.getUserByUsername(uInfo.userName)
    val devicesToAdd = read[List[AddDevice]](devicesAsString)
    logger.debug(s"lDevices: ${devicesToAdd.mkString(", ")}; uId: ${user.id}; tokenUserId: ${uInfo.id}")
    val createdDevices = Devices.bulkCreateDevice(user.id, devicesToAdd)
    if (!isCreatedDevicesSuccess(createdDevices)) {
      halt(400, createdDevicesToJson(createdDevices))
    }
    Ok(createdDevicesToJson(createdDevices))
  }

  val updateDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("updateDevice")
      summary "Update a device."
      description "Update a device. The specified device will see all its attributes replaced by the new provided one."
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("id").
        description("hwDeviceId of the device that will be updated"),
        bodyParam[UpdateDevice]("Device as JSON").
        description("Json of the device")
      ))

  put("/:id", operation(updateDevice)) {
    logger.debug("devices: put(/:id)")
    val uInfo = auth.get
    implicit val realmName: String = uInfo.realmName
    val updateDevice = extractUpdateDevice
    val addDevice = AddDevice(updateDevice.hwDeviceId, updateDevice.description, updateDevice.deviceType, updateDevice.groupList)
    Devices.updateDevice(updateDevice.ownerId, addDevice, updateDevice.deviceConfig, updateDevice.apiConfig)
  }

  val getAllDevicesFromUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStubs]]("getUserFromToken")
      summary "List all the devices of one user"
      description "For the moment does not support pagination"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[Int]("page").
        description("Number of the page requested (starts at 0)"),
        pathParam[Int]("size").
        description("Number of devices to be contained in a page")
      ))

  get("/page/:page/size/:size", operation(getAllDevicesFromUser)) {

    logger.debug("devices: get(/)")
    val userInfo = auth.get
    val pageNumber = params("page").toInt
    val pageSize = params("size").toInt
    implicit val realmName: String = userInfo.realmName
    Users.fullyCreateUser(user.id)
    val devicesOfTheUser = Users.listAllDevicesStubsOfAUser(pageNumber, pageSize, userInfo.userName)
    logger.debug(s"res: ${devicesOfTheUser._1.mkString(", ")}")
    implicit val formats: DefaultFormats.type = DefaultFormats

    write(ReturnDeviceStubList(devicesOfTheUser._2, devicesOfTheUser._1.sortBy(d => d.hwDeviceId)))

  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getClass.toString, e.getMessage))
  }

  private def getHwDeviceId: String = {
    params("id")
  }

  private def isCreatedDevicesSuccess(createdDevicesResponse: List[String]) = !createdDevicesResponse.mkString.contains(""""state":"notok"""")
  private def createdDevicesToJson(createdDevicesResponse: List[String]) = s"[${createdDevicesResponse.mkString(",")}]"
  private def extractUpdateDevice = {
    val deviceJson = request.body
    parse(deviceJson).extractOpt[UpdateDevice].getOrElse {
      halt(400, FeUtils.createServerError("incorrectFormat", "device structure incorrect"))
    }
  }
}

