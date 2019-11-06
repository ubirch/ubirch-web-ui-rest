package com.ubirch.webui.server.rest

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.member._
import com.ubirch.webui.core.GraphOperations
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import com.ubirch.webui.server.models.UpdateDevice
import org.joda.time.DateTime
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
  protected val applicationDescription = "Device-related requests."

  // Sets up automatic case class to JSON output serialization
  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format and increase prometheus counter.
  before() {
    contentType = formats("json")
  }

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName).
    description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")

  val getOneDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[DeviceFE]("getOneDevice")
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
    val user = UserFactory.getByUsername(uInfo.userName)
    val device = DeviceFactory.getByHwDeviceId(hwDeviceId)
    device.isUserAuthorized(user)
  }

  val searchForDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceFE]]("searchForDevices")
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
    val user = UserFactory.getByUsername(uInfo.userName)
    DeviceFactory.searchMultipleDevices(search).map { d => d.isUserAuthorized(user) }
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
    val device = DeviceFactory.getByHwDeviceId(hwDeviceId)
    UserFactory.getByUsername(uInfo.userName).deleteOwnDevice(device)
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
    val user = UserFactory.getByUsername(uInfo.userName)
    val devicesToAdd = read[List[AddDevice]](devicesAsString)
    val createdDevices = user.createMultipleDevices(devicesToAdd)
    logger.debug("created devices: " + createdDevices.map{d => d.toJson}.mkString("; "))
    if (!isCreatedDevicesSuccess(createdDevices)) {
      logger.debug("one ore more device failed to be create" + createdDevicesToJson(createdDevices))
      halt(400, createdDevicesToJson(createdDevices))
    }
    logger.debug("creation device OK: " + createdDevicesToJson(createdDevices))
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
    val device = DeviceFactory.getByHwDeviceId(updateDevice.hwDeviceId)
    val newOwner = UserFactory.getByKeyCloakId(updateDevice.ownerId)
    device.updateDevice(newOwner, addDevice, updateDevice.deviceConfig, updateDevice.apiConfig)
  }

  val getAllDevicesFromUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStub]]("getUserFromToken")
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
    logger.debug("devices: get(/page/:page/size/:size)")
    val userInfo = auth.get
    val pageNumber = params("page").toInt
    val pageSize = params("size").toInt
    implicit val realmName: String = userInfo.realmName
    val user: User = UserFactory.getByUsername(userInfo.userName)
    user.fullyCreate()
    //Users.fullyCreateUser(user.id)
    val devicesOfTheUser = user.getOwnDeviceGroup.getDevicesPagination(pageNumber, pageSize)
    logger.debug(s"res: ${devicesOfTheUser.mkString(", ")}")

    implicit val formats: DefaultFormats.type = DefaultFormats
    write(ReturnDeviceStubList(user.getNumberOfOwnDevices, devicesOfTheUser.sortBy(d => d.hwDeviceId)))

  }


  val getBulkUpps: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getBulkUppsOfDevice")
      summary "Number of UPPs that the specified devices created during the specified timeframe"
      description "Number of UPPs that the specified devices created during the specified timeframe"
      tags "Devices"
      parameters (
      swaggerTokenAsHeader,
      pathParam[String]("from").
        description("Date in Joda time"),
      pathParam[String]("to").
        description("Date in Joda time"),
      pathParam[String]("hwDeviceIds").
        description("List of hwDeviceIds")
    ))


  get("/:from/:to/hwDeviceIds", operation(getBulkUpps)) {
    logger.info("devices: get(/uppCreated)")
    val userInfo = auth.get
    val hwDevicesIdString = params("hwDeviceIds").split(",").toList
    //val hwDeviceIds = read[List[String]](hwDevicesIdString)

    val dateFrom = DateTime.parse(params("from").toString).getMillis
    val dateTo = DateTime.parse(params("to").toString).getMillis
    implicit val realmName: String = userInfo.realmName

    val res = GraphOperations.bulkGetUpps(hwDevicesIdString, dateFrom, dateTo)
    Ok(uppdsToJson(res))
  }

  error {
    case e =>
      logger.error(FeUtils.createServerError(e.getClass.toString, e.getMessage))
      halt(400, FeUtils.createServerError(e.getClass.toString, e.getMessage))
  }

  private def getHwDeviceId: String = {
    params("id")
  }

  private def isCreatedDevicesSuccess(createdDevicesResponse: List[DeviceCreationState]) = !createdDevicesResponse.exists(cD => cD.isInstanceOf[DeviceCreationFail])

  private def createdDevicesToJson(createdDevicesResponse: List[DeviceCreationState]) = {
    "[" + createdDevicesResponse.map{d => d.toJson}.mkString(", ") + "]"
  }

  private def uppdsToJson(uppsNumber: List[UppState]) = {
    "[" + uppsNumber.map{d => d.toJson}.mkString(", ") + "]"
  }

  private def extractUpdateDevice = {
    val deviceJson = request.body
    parse(deviceJson).extractOpt[UpdateDevice].getOrElse {
      halt(400, FeUtils.createServerError("incorrectFormat", "device structure incorrect"))
    }
  }
}

