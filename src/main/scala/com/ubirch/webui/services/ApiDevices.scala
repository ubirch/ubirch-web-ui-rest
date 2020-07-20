package com.ubirch.webui.services

import java.time.{ LocalDate, ZoneId }
import java.util.concurrent.TimeUnit

import com.google.common.base.{ Supplier, Suppliers }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.batch.{ Batch, ResponseStatus, SIM, SIMClaiming, Session => ElephantSession }
import com.ubirch.webui.models.{ BootstrapInfo, Elements, Headers, UpdateDevice }
import com.ubirch.webui.models.Exceptions.{ GroupNotFound, HexDecodingError, NotAuthorized }
import com.ubirch.webui.models.authentification.AuthenticationSupport
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.member._
import com.ubirch.webui.FeUtils
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.graph.GraphOperations
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.util.Util
import org.joda.time.DateTime
import org.json4s.{ DefaultFormats, Formats, _ }
import org.json4s.jackson.Serialization.{ read, write }
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.servlet.{ FileUploadSupport, MultipartConfig }
import org.scalatra.swagger._

import scala.collection.immutable

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

  def swaggerTokenAsHeader: SwaggerSupportSyntax.ParameterBuilder[String] = headerParam[String](FeUtils.tokenHeaderName)
    .description("Token of the user. ADD \"bearer \" followed by a space) BEFORE THE TOKEN OTHERWISE IT WON'T WORK")
  //.example(SwaggerDefaultValues.BEARER_TOKEN)

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
      consumes "multipart/form-data"
      parameters (
        swaggerTokenAsHeader,
        Parameter(
          `name` = "file",
          `description` = Some("Batch of records to import"),
          `type` = DataType("file"),
          `paramType` = ParamType.Form,
          `defaultValue` = None,
          `allowableValues` = AllowableValues.AnyValue,
          `required` = true
        ),
          Parameter(
            `name` = "skip_header",
            `description` = Some("Weather or not to skip the first row of the file."),
            `type` = DataType("boolean"),
            `paramType` = ParamType.Form,
            `defaultValue` = None,
            `allowableValues` = AllowableValues.AnyValue,
            `required` = true
          ),
            Parameter(
              `name` = "batch_type",
              `description` = Some("Describes the type of the file to be imported."),
              `type` = DataType("string"),
              `paramType` = ParamType.Form,
              `defaultValue` = None,
              `allowableValues` = AllowableValues.AnyValue,
              `required` = true
            ),
              Parameter(
                `name` = "batch_provider",
                `description` = Some("Describes the provider of the data"),
                `type` = DataType("string"),
                `paramType` = ParamType.Form,
                `defaultValue` = None,
                `allowableValues` = AllowableValues.AnyValue,
                `required` = true
              ),
                Parameter(
                  `name` = "batch_description",
                  `description` = Some("Brief description of the file."),
                  `type` = DataType("string"),
                  `paramType` = ParamType.Form,
                  `defaultValue` = None,
                  `allowableValues` = AllowableValues.AnyValue,
                  `required` = true
                ),
                  Parameter(
                    `name` = "batch_tags",
                    `description` = Some("Tags that help categorize the contents of the file"),
                    `type` = DataType("string"),
                    `paramType` = ParamType.Form,
                    `defaultValue` = None,
                    `allowableValues` = AllowableValues.AnyValue,
                    `required` = true
                  )
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
            .getOrElse(halt(400, FeUtils.createServerError("Wrong params", "No batch_provider found")))
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
        pathParam[String]("id").description("hwDeviceId of the device") //.example(SwaggerDefaultValues.HW_DEVICE_ID)
      ))

  get("/:id", operation(getOneDevice)) {
    logger.info("devices: get(/:id)")
    whenLoggedInAsUser { (userInfo, user) =>
      implicit val realmName: String = userInfo.realmName
      val hwDeviceId = getHwDeviceId
      DeviceFactory.getByHwDeviceId(hwDeviceId) match {
        case Left(_) => stopBadUUID(hwDeviceId)
        case Right(device) => device.ifUserAuthorizedReturnDeviceFE(user)
      }
    }
  }

  get("/claim/stats") {

    import org.json4s.JsonDSL._

    def getStats(provider: String, userInfo: UserInfo) = {
      val imported = GroupFactory.getByName(Util.getProviderGroupName(provider))(userInfo.realmName).getMaxCount()
      val claimed = try {
        GroupFactory.getByName(Util.getProviderClaimedDevicesName(provider))(userInfo.realmName).getMaxCount()
      } catch {
        case _: GroupNotFound =>
          logger.warn(s"GET devices/claim/stats ${Util.getProviderClaimedDevicesName(provider)} group not found.")
          0
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

    whenLoggedInAsUser { (userInfo, _) =>

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
        headerParam[String](Headers.X_UBIRCH_IMSI).description("IMSI of the SIM Card"), //.example(SwaggerDefaultValues.IMSI),
        headerParam[String](Headers.X_UBIRCH_CREDENTIAL).description("Password of the device, base64 encoded") //.example(SwaggerDefaultValues.X_UBIRCH_CREDENTIAL)
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
        pathParam[String]("search").description("String that will be used for the search")
      ))

  get("/search/:search", operation(searchForDevices)) {
    logger.info("devices: get(/search/:search)")
    whenLoggedInAsUser { (userInfo, user) =>
      val search = params("search")
      implicit val realmName: String = userInfo.realmName
      DeviceFactory.searchMultipleDevices(search).filter { d => d.isUserAuthorized(user) }.map { d => d.toDeviceFE }
    }
  }

  val deleteDevice: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Boolean]("deleteOneDevice")
      summary "Delete a single device"
      description "Delete one device belonging to a user from his hwDeviceId"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("id").description("hwDeviceId of the device that will be deleted") //.example(SwaggerDefaultValues.HW_DEVICE_ID)
      ))

  delete("/:id", operation(deleteDevice)) {
    logger.debug("devices: delete(/:id)")
    whenLoggedInAsUser { (userInfo, user) =>
      val hwDeviceId = getHwDeviceId
      implicit val realmName = userInfo.realmName
      DeviceFactory.getByHwDeviceId(hwDeviceId) match {
        case Left(_) => stopBadUUID(hwDeviceId)
        case Right(device) => UserFactory.getByUsername(userInfo.userName).deleteOwnDevice(device)
      }
    }
  }

  val addBulkDevices: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("addBulkDevices")
      summary "Add multiple devices."
      description "Add multiple devices."
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        bodyParam[List[AddDevice]]("listDevices")
        .description("List of device representation to add [{hwDeviceId: String, description: String, deviceType: String, listGroups: List[String]}].")
      //.example(write(SwaggerDefaultValues.ADD_DEVICE_LIST))
      ))

  post("/", operation(addBulkDevices)) {
    logger.debug("devices: post(/)")
    whenLoggedInAsUser { (userInfo, user) =>
      implicit val realmName: String = userInfo.realmName
      val devicesAsString: String = request.body
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
          "{ \"reqType\":\"claim\", \"devices\":[{\"hwDeviceId\": \"\", \"secondaryIndex\":\"100000000001096\", \"description\": \"\"}], \"tags\":[\"testkit\",\"watersensor\"], \"prefix\":\"HOLA\" }")
      ))

  post("/elephants", operation(bulkDevices)) {
    logger.debug("devices: post(/elephants)")

    whenLoggedInAsUser { (userInfo, _) =>

      val minLengthIds = 5

      implicit val session: ElephantSession = ElephantSession(userInfo.id, userInfo.realmName, userInfo.userName)

      val maybeBulkRequest = for {
        br <- parsedBody.extractOpt[BulkRequest]
      } yield {
        val trimmedBr = br.copy(devices = br.devices.map(x => x.copy(hwDeviceId = x.hwDeviceId.trim, secondaryIndex = x.secondaryIndex.trim)))
        (br.reqType, trimmedBr)
      }

      if (maybeBulkRequest.exists(_._2.devices.isEmpty)) {
        halt(400, FeUtils.createServerError("general: wrong params", "No devices found"))
      }

      if (maybeBulkRequest.exists(x => x._2.devices.exists(_.hwDeviceId.isEmpty) && x._2.devices.exists(_.secondaryIndex.isEmpty))) {
        halt(400, FeUtils.createServerError("general: wrong params", "No Ids found"))
      }

      if (maybeBulkRequest.exists(x => x._2.devices.exists(_.hwDeviceId.contains(" ")) && x._2.devices.exists(_.secondaryIndex.contains(" ")))) {
        halt(400, FeUtils.createServerError("general: wrong params", "Ids can't have blank spaces"))
      }

      maybeBulkRequest match {
        case Some(("creation", br)) =>
          if (br.devices.exists(d => d.hwDeviceId.isEmpty || d.hwDeviceId.equals("") || d.hwDeviceId.length < minLengthIds)) {
            halt(400, FeUtils.createServerError("ID: wrong params", "At least one device body doesn’t contain a valid ID field"))
          } else {
            deviceNormalCreation(br)
          }
        case Some(("claim", br)) =>
          if (br.devices.exists(d => d.secondaryIndex.isEmpty || d.secondaryIndex.equals("") || d.secondaryIndex.length < minLengthIds)) {
            halt(400, FeUtils.createServerError("IMSI: wrong params", "At least one device body doesn’t contain a valid IMSI field"))
          } else {
            deviceClaiming(br)
          }
        case Some(what) => halt(400, FeUtils.createServerError("general: wrong params", "Wrong reqType. " + what._1))
        case _ =>
          halt(400, FeUtils.createServerError("general: wrong params", "general errors, are some fields missing?"))
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
        pathParam[String]("id")
        .description("hwDeviceId of the device that will be updated"),
        //.example(SwaggerDefaultValues.HW_DEVICE_ID),
        bodyParam[DeviceFE]("Device as JSON")
        .description("Json of the device")
      //.example(write(SwaggerDefaultValues.UPDATE_DEVICE))
      ))

  put("/:id", operation(updateDevice)) {
    logger.debug("devices: put(/:id)")
    //TODO: add checks that only the user that owns the device can update it
    whenLoggedInAsUser { (userInfo, user) =>
      implicit val realmName: String = userInfo.realmName
      val updateDevice: DeviceFE = extractUpdateDevice
      DeviceFactory.getByHwDeviceId(updateDevice.hwDeviceId) match {
        case Left(_) => stopBadUUID(updateDevice.hwDeviceId)
        case Right(device) =>
          if (device.isUserAuthorized(user)) {
            device
              .updateDevice(updateDevice)
              .toDeviceFE
          } else {
            halt(400, FeUtils.createServerError("not authorized", s"device with hwDeviceId ${device.getHwDeviceId} does not belong to user ${user.getUsername}"))
          }
      }
    }
  }

  val getAllDevicesFromUser: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[List[DeviceStub]]("getAllDevicesFromUser")
      summary "List all the devices of one user"
      description "For the moment does not support pagination"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[Int]("page")
        .description("Number of the page requested (starts at 0)"),
        //.example("0"),
        pathParam[Int]("size")
        .description("Number of devices to be contained in a page")
      //.example("10")
      ))

  get("/page/:page/size/:size", operation(getAllDevicesFromUser)) {
    logger.debug("devices: get(/page/:page/size/:size)")
    whenLoggedInAsUser { (userInfo, user) =>
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

  }

  val getBulkUpps: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getBulkUppsOfDevice")
      summary "Number of UPPs that the specified devices created during the specified timeframe"
      description "Number of UPPs that the specified devices created during the specified timeframe"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        pathParam[String]("from")
        .description("Date in Joda time"),
        //.example("2019-12-13T21:39:45.618-08:00"),
        pathParam[String]("to")
        .description("Date in Joda time"),
        //.example("2019-12-14T21:39:45.618-08:00"),
        bodyParam[String]("hwDeviceIds")
        .description("List of hwDeviceIds, comma separated")
      //.example(SwaggerDefaultValues.HW_DEVICE_ID + ",a6b63106-662d-4fda-836e-96833d18b936")
      ))

  post("/state/:from/:to", operation(getBulkUpps)) {
    logger.info("devices: post(/state/:from/:to/:hwDeviceIds)")
    whenLoggedInAsUser { (userInfo, user) =>
      val hwDevicesIdString = request.body.split(",").toList

      val dateFrom = DateTime.parse(params("from").toString).getMillis
      val dateTo = DateTime.parse(params("to").toString).getMillis
      implicit val realmName: String = userInfo.realmName

      val res = GraphOperations.bulkGetUpps(user, hwDevicesIdString, dateFrom, dateTo)
      Ok(uppsToJson(res))
    }
  }

  val getBulkUppsDaily: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getBulkUppsOfDeviceDaily")
      summary "Number of UPPs that the specified devices created since the beginning of the day (UTC time)"
      description "Number of UPPs that the specified devices created since the beginning of the day (UTC time)"
      tags "Devices"
      parameters (
        swaggerTokenAsHeader,
        bodyParam[String]("hwDeviceIds")
        .description("List of hwDeviceIds, comma separated")
      //.example(SwaggerDefaultValues.HW_DEVICE_ID + ",a6b63106-662d-4fda-836e-96833d18b936")
      ))

  post("/state/daily", operation(getBulkUppsDaily)) {
    logger.debug("devices: post(/state/daily)")
    whenLoggedInAsUser { (userInfo, user) =>
      val hwDevicesIdString = request.body.split(",").toList

      val zoneId = ZoneId.of("Z")
      val today = LocalDate.now(zoneId)
      val beginningDayUtcMillis = today.atStartOfDay(zoneId).toInstant.toEpochMilli
      val nowUtcMillis = System.currentTimeMillis()
      implicit val realmName: String = userInfo.realmName

      val res = GraphOperations.bulkGetUpps(user, hwDevicesIdString, beginningDayUtcMillis, nowUtcMillis)
      Ok(uppsToJson(res))
    }
  }

  val getLastHash: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[LastHash]("getLastHashDevice")
      summary "Get the last hash produced by a device"
      description "Get the last hash that was sent to ubirch by the specified device." +
      "This return value can then be used to verify that the hash has been stored in the blockchain." +
      "In case of a burst of messages sent in a relative small time window, this endpoint might not return the " +
      "absolute last message."
      tags "Devices"
      parameters (
      swaggerTokenAsHeader,
      pathParam[String]("id")
        .description("hwDeviceId of the desired device")
    ))

  get("/lastHash/:id", operation(getLastHash)) {
    logger.debug(s"devices: get(/lastHash/$getHwDeviceId)")
    whenLoggedInAsUser { (userInfo, user) =>
      val hwDeviceId = getHwDeviceId
      implicit val realmName: String = userInfo.realmName

      DeviceFactory.getByHwDeviceId(hwDeviceId) match {
        case Left(_) => stopBadUUID(hwDeviceId)
        case Right(device) =>
          if (device.isUserAuthorized(user)) {
            device.getLastHash
          } else {
            halt(400, FeUtils.createServerError("not authorized", s"device with hwDeviceId ${device.getHwDeviceId} does not belong to user ${user.getUsername}"))
          }
      }
    }
  }

  error {
    case e =>
      logger.info(FeUtils.createServerError(e.getClass.toString, e.getMessage))
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
    parse(deviceJson).extractOpt[DeviceFE].getOrElse {
      halt(400, FeUtils.createServerError("incorrectFormat", "device structure incorrect"))
    }
  }

  private def deviceNormalCreation(bulkRequest: BulkRequest)(implicit session: ElephantSession) = {
    val user = UserFactory.getByUsername(session.username)(session.realm)
    val enrichedDevices: List[AddDevice] = bulkRequest.devices.map { d => d.addToAttributes(Map(Elements.CLAIMING_TAGS_NAME -> List(bulkRequest.tags.mkString(", ")))) }
    val createdDevices = user.createMultipleDevices(enrichedDevices)
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

  private def stopBadUUID(hwDeviceId: String) = {
    halt(400, FeUtils.createServerError("Bad hwDeviceId", s"provided hwDeviceId: $hwDeviceId is not a valid UUID"))
  }

  private def stopIfProviderDoesntExist(providerName: String)(implicit realmName: String) = {
    try {
      GroupFactory.getByName(Util.getProviderGroupName(providerName))
    } catch {
      case _: GroupNotFound =>
        halt(401, FeUtils.createServerError("Invalid Provider", s"$providerName is not an authorized provider."))
    }
  }

}

