package com.ubirch.webui.server.rest

import java.time.{ LocalDate, ZoneId }
import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.batch.{ Batch, ResponseStatus, SIM, SIMClaiming, Session => ElephantSession }
import com.ubirch.webui.core.Exceptions.{ GroupNotFound, HexDecodingError, NotAuthorized }
import com.ubirch.webui.core.GraphOperations
import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.group.GroupFactory
import com.ubirch.webui.core.structure.member._
import com.ubirch.webui.server.FeUtils
import com.ubirch.webui.server.authentification.AuthenticationSupport
import com.ubirch.webui.server.models.{ BootstrapInfo, UpdateDevice }
import org.joda.time.DateTime
import org.json4s.{ DefaultFormats, Formats, _ }
import org.json4s.jackson.Serialization.{ read, write }
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.servlet.{ FileUploadSupport, MultipartConfig }
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

class ApiDevices(implicit val swagger: Swagger)
  extends ScalatraServlet
  with FileUploadSupport
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging
  with AuthenticationSupport
  with ConfigBase {

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS, PUT")
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"))
  }

  // Adding max file size and max request size for Multipart requests
  configureMultipartHandling(MultipartConfig(
    maxFileSize = Some(30 * 1024 * 1024),
    maxRequestSize = Some(100 * 1024 * 1024)
  ))

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

  /**
    * Represents the endpoint that allows a flash batch import of devices.
    */

  val batchImportSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[ResponseStatus]("batch")
      summary "Imports devices in batch from file (ADMIN only)"
      description "Imports devices into the system from a well-know csv file. \n " +
      "The endpoint allows the upload of a file for import. \n " +
      "The encode type of the request should be multipart/form-data \n" +
      "See this format example: https://github.com/ubirch/ubirch-web-ui-rest/blob/master/server/src/main/scala/com/ubirch/webui/batch/sample.csv"
      tags ("Devices", "Batch", "Import")
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("skip_header").description("Weather or not to skip the first row of the file."),
        pathParam[String]("batch_type").description("Describes the type of the file to be imported."),
        pathParam[String]("batch_description").description("Brief description of the file."),
        pathParam[String]("batch_tags").description("Tags that help categorize the contents of the file")
      ))

  post("/batch", operation(batchImportSwagger)) {

    whenAdmin { (userInfo, _) =>

      implicit val session: ElephantSession = ElephantSession(userInfo.id, userInfo.realmName, userInfo.userName)

      val maybeBatch = for {
        tp <- params.get("batch_type")
        b <- Batch.fromString(tp)
      } yield b

      maybeBatch match {
        case Some(batch) =>

          val fileItem = fileParams.get("file")
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No file in request")))
          val provider = params.getAs[String]("batch_provider")
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No provider found")))
            .replaceAll(" ", "_")
          stopIfProviderDoesntExist(provider)(session.realm)
          val skipHeader = params.getAs[Boolean]("skip_header")
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No skip_header found")))
          val desc = params.get("batch_description")
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No batch_description provided")))
            .take(500)
          val tags = params.get("batch_tags")
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No batch_tags provided")))

          logger.info("Received Batch Processing Request batch_provider={} batch_type={} batch_description={} skip_header={} tags={}", provider, batch.value, desc, skipHeader, tags)

          batch.ingest(provider, fileItem.name, fileItem.getInputStream, skipHeader, desc, tags)

        case None =>
          logger.error("Unrecognized batch_type")
          halt(400, FeUtils.createServerError("Wrong params", "No batch_type provided."))

      }
    }

  }

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

  get("/claim/stats") {

    import org.json4s.JsonDSL._

    def getStats(provider: String, userInfo: UserInfo) = {
      val imported = GroupFactory.getByName(Util.getProviderGroupName(provider))(userInfo.realmName).getMaxCount()
      val claimed = try {
        GroupFactory.getByName(Util.getProviderClaimedDevicesName(provider))(userInfo.realmName).getMaxCount()
      } catch {
        case _: GroupNotFound => 0
        case e: Throwable => throw e
      }
      val unclaimed = imported - claimed
      val stats = ("provider" -> provider) ~ ("imported" -> imported) ~ ("claimed" -> claimed) ~ ("unclaimed" -> unclaimed)
      stats
    }

    def memoizedStats(provider: String, userInfo: UserInfo) = Suppliers.memoizeWithExpiration(new Supplier[JObject] {
      override def get(): JObject = {
        logger.info("Getting value")
        getStats(provider, userInfo)
      }
    }, 5, TimeUnit.MINUTES)

    whenLoggedIn { (userInfo, _) =>

      params.get("batch_provider") match {
        case Some(provider) =>
          stopIfProviderDoesntExist(provider)(userInfo.realmName)
          memoizedStats(provider, userInfo).get()
        case None =>
          halt(400, FeUtils.createServerError("Wrong params", "No batch_provider provided."))

      }

    }

  }

  /**
    * Represents and endpoint for making a Bootstrap of a device.
    */

  val getBootstrap: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[BootstrapInfo]("getBootstrap")
      summary "Get the pin for a SIM Card"
      description "Returns the pin for a SIM Card based on its IMSI"
      tags ("Devices", "SIM", "Bootstrap")
      parameters (
        headerParam[String](Headers.X_UBIRCH_IMSI).
        description("IMSI of the SIM Card"),
        headerParam[String](Headers.X_UBIRCH_CREDENTIAL).
        description("Password of the device, base64 encoded")
      ))

  get("/bootstrap", operation(getBootstrap)) {

    contentType = formats("json")

    val imsi = request.headers
      .get(Headers.X_UBIRCH_IMSI)
      .filter(_.nonEmpty)
      .getOrElse(halt(400, FeUtils.createServerError("Invalid Parameters", s"No ${Headers.X_UBIRCH_IMSI} header provided")))

    val password = request.headers
      .get(Headers.X_UBIRCH_CREDENTIAL)
      .filter(_.nonEmpty)
      .getOrElse(halt(400, FeUtils.createServerError("Invalid Parameters", s"No ${Headers.X_UBIRCH_CREDENTIAL} header provided")))

    val device = DeviceFactory.getBySecondaryIndex(SIM.IMSI_PREFIX + imsi + SIM.IMSI_SUFFIX, SIM.IMSI.name)(theRealmName)

    if (device.isClaimed) {
      try {
        Auth.auth(device.getHwDeviceId, password)
      } catch {
        case e: NotAuthorized =>
          logger.warn(s"Device not authorized [{}] [{}] [{}]: ", device.getHwDeviceId, e.getMessage, theRealmName)
          halt(401, FeUtils.createServerError("Authentication", e.getMessage))
        case e: HexDecodingError =>
          halt(400, FeUtils.createServerError("Invalid base64 value for password", e.getMessage))
        case e: Throwable =>
          logger.error(FeUtils.createServerError(e.getClass.toString, e.getMessage))
          halt(500, FeUtils.createServerError("Internal error", e.getMessage))
      }

      device.getAttributes.getOrElse(SIM.PIN.name, Nil) match {
        case Nil => NotFound()
        case List(pin) => Ok(BootstrapInfo(encrypted = false, pin))
        case _ =>
          logger.warn("Device with multiple PINS Device [{}]", device.getHwDeviceId)
          Conflict()
      }
    } else {
      logger.warn("Bootstrap Error: Device[{}] has not been claimed yet.", imsi)
      halt(403, FeUtils.createServerError("Bootstrap Error", "Device has not been claimed yet."))
    }

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
    logger.debug("created devices: " + createdDevices.map { d => d.toJson }.mkString("; "))
    if (!isCreatedDevicesSuccess(createdDevices)) {
      logger.debug("one ore more device failed to be created:" + createdDevicesToJson(createdDevices))
      halt(400, createdDevicesToJson(createdDevices))
    }
    logger.debug("creation device OK: " + createdDevicesToJson(createdDevices))
    Ok(createdDevicesToJson(createdDevices))
  }

  val bulkDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addBulkDevices")
      summary "Create or claim multiple devices."
      description "Create or claim multiple devices."
      tags ("Devices", "Claim", "Create")
      parameters (
        swaggerTokenAsHeader,
        bodyParam[BulkRequest]("BulkRequest").
        description("List of device representation to create \n " +
          "{ \"reqType\":\"creation\", \"devices\":[{\"hwDeviceId\": \"123456789\", \"secondaryIndex\":\", \"description\": \"Hello\"}] } \n " +
          "{ \"reqType\":\"claim\", \"devices\":[{\"hwDeviceId\": \"\", \"secondaryIndex\":\"100000000001096\", \"description\": \"\"}], \"tags\":\"tag1, tag2, tag3\", \"prefix\":\"HOLA\" }")
      ))

  post("/elephants", operation(bulkDevices)) {
    logger.debug("devices: post(/elephants)")

    whenLoggedIn { (userInfo, _) =>

      implicit val session: ElephantSession = ElephantSession(userInfo.id, userInfo.realmName, userInfo.userName)

      val maybeBulkRequest = for {
        br <- parsedBody.extractOpt[BulkRequest]
      } yield (br.reqType, br)

      if (maybeBulkRequest.exists(_._2.devices.isEmpty)) {
        halt(400, FeUtils.createServerError("Wrong params", "No devices found"))
      }

      maybeBulkRequest match {
        case Some(("creation", br)) => deviceNormalCreation(br)
        case Some(("claim", br)) => deviceClaiming(br)
        case Some(what) => halt(400, FeUtils.createServerError("Wrong params", "Wrong req_type. " + what._1))
        case _ =>
          halt(400, FeUtils.createServerError("Wrong params", "No req_type found."))
      }

    }

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
    val updateDevice: UpdateDevice = extractUpdateDevice
    val device = DeviceFactory.getByHwDeviceId(updateDevice.hwDeviceId)
    val addDevice = AddDevice(updateDevice.hwDeviceId, updateDevice.description, updateDevice.deviceType, updateDevice.groupList, secondaryIndex = device.getSecondaryIndex)
    val newOwner = UserFactory.getByKeyCloakId(updateDevice.ownerId)
    device.updateDevice(List(newOwner), addDevice, updateDevice.deviceConfig, updateDevice.apiConfig)
  }

  val getAllDevicesFromUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStub]]("getAllDevicesFromUser")
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
        bodyParam[String]("hwDeviceIds").
        description("List of hwDeviceIds, comma separated")
      ))

  post("/state/:from/:to", operation(getBulkUpps)) {
    logger.info("devices: post(/state/:from/:to/:hwDeviceIds)")
    val userInfo = auth.get
    val hwDevicesIdString = request.body.split(",").toList
    //val hwDeviceIds = read[List[String]](hwDevicesIdString)

    val dateFrom = DateTime.parse(params("from").toString).getMillis
    val dateTo = DateTime.parse(params("to").toString).getMillis
    implicit val realmName: String = userInfo.realmName

    val res = GraphOperations.bulkGetUpps(hwDevicesIdString, dateFrom, dateTo)
    Ok(uppsToJson(res))
  }

  val getBulkUppsDaily: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getBulkUppsOfDeviceDaily")
      summary "Number of UPPs that the specified devices created since the beginning of the day (UTC time)"
      description "Number of UPPs that the specified devices created since the beginning of the day (UTC time)"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        bodyParam[String]("hwDeviceIds").
        description("List of hwDeviceIds, comma separated")
      ))

  post("/state/daily", operation(getBulkUppsDaily)) {
    logger.debug("devices: post(/state/daily)")
    val userInfo = auth.get
    val hwDevicesIdString = request.body.split(",").toList

    val zoneId = ZoneId.of("Z")
    val today = LocalDate.now(zoneId)
    val beginningDayUtcMillis = today.atStartOfDay(zoneId).toInstant.toEpochMilli
    val nowUtcMillis = System.currentTimeMillis()
    implicit val realmName: String = userInfo.realmName

    val res = GraphOperations.bulkGetUpps(hwDevicesIdString, beginningDayUtcMillis, nowUtcMillis)
    Ok(uppsToJson(res))
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
    "[" + createdDevicesResponse.map { d => d.toJson }.mkString(", ") + "]"
  }

  private def uppsToJson(uppsNumber: List[UppState]) = {
    "[" + uppsNumber.map { d => d.toJson }.mkString(", ") + "]"
  }

  private def extractUpdateDevice = {
    val deviceJson = request.body
    parse(deviceJson).extractOpt[UpdateDevice].getOrElse {
      halt(400, FeUtils.createServerError("incorrectFormat", "device structure incorrect"))
    }
  }

  private def deviceNormalCreation(bulkRequest: BulkRequest)(implicit session: ElephantSession) = {
    val user = UserFactory.getByUsername(session.username)(session.realm)
    val createdDevices = user.createMultipleDevices(bulkRequest.devices)
    logger.debug("created devices: " + createdDevices.map { d => d.toJson }.mkString("; "))
    if (!isCreatedDevicesSuccess(createdDevices)) {
      logger.debug("one ore more device failed to be created:" + createdDevicesToJson(createdDevices))
      halt(400, createdDevicesToJson(createdDevices))
    }
    logger.debug("creation device OK: " + createdDevicesToJson(createdDevices))
    Ok(createdDevicesToJson(createdDevices))
  }

  private def deviceClaiming(bulkRequest: BulkRequest)(implicit session: ElephantSession) = {
    val createdDevices = SIMClaiming.claim(bulkRequest)
    if (!isCreatedDevicesSuccess(createdDevices)) {
      logger.debug("one ore more device failed to be claimed:" + createdDevicesToJson(createdDevices))
      halt(400, createdDevicesToJson(createdDevices))
    }
    logger.debug("device claimed OK: " + createdDevicesToJson(createdDevices))
    Ok(createdDevicesToJson(createdDevices))
  }

  private def stopIfProviderDoesntExist(providerName: String)(implicit realmName: String) = {
    try {
      GroupFactory.getByName(Util.getProviderGroupName(providerName))
    } catch {
      case _: GroupNotFound => halt(401, s"$providerName is not an authorized provider.")
    }
  }

}

