package com.ubirch.webui.core.structure.member

import com.ubirch.webui.core.Exceptions.{InternalApiException, PermissionException}
import com.ubirch.webui.core.connector.janusgraph.{ConnectorType, GremlinConnector, GremlinConnectorFactory}
import com.ubirch.webui.core.structure._
import com.ubirch.webui.core.structure.group.{Group, GroupFactory}
import gremlin.scala.{Key, P}
import org.keycloak.admin.client.resource.UserResource

import scala.collection.JavaConverters._

class Device(keyCloakMember: UserResource)(implicit realmName: String)
  extends Member(keyCloakMember) {

  def getHwDeviceId: String = this.getUsername

  def updateDevice(newOwner: User, deviceUpdateStruct: AddDevice, deviceConfig: String, apiConfig: String): Device = {
    val deviceRepresentation = toRepresentation
    deviceRepresentation.setLastName(deviceUpdateStruct.description)

    val newDeviceTypeGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(deviceUpdateStruct.deviceType))

    val oldOwner = getOwner
    if (!newOwner.isEqual(oldOwner)) {
      changeOwnerOfDevice(newOwner, oldOwner)
    }

    val deviceAttributes = Map(
      Elements.ATTRIBUTES_DEVICE_GROUP_NAME -> List(deviceConfig).asJava,
      Elements.ATTRIBUTES_API_GROUP_NAME -> List(apiConfig).asJava
    ).asJava

    deviceRepresentation.setAttributes(deviceAttributes)

    keyCloakMember.update(deviceRepresentation)
    val excludedGroupNames: List[String] =
      List(getApiConfigGroup.name, getOwner.getOwnDeviceGroup.name)
    leaveOldGroupsJoinNewGroups(deviceUpdateStruct.listGroups :+ newDeviceTypeGroup.name, excludedGroupNames)
    getUpdatedDevice
  }

  def getApiConfigGroup: Group =
    getAllGroups.filter(p => p.name.contains(Elements.PREFIX_API)).head

  def getUpdatedDevice: Device = DeviceFactory.getByKeyCloakId(memberId)

  protected[structure] def changeOwnerOfDevice(newOwner: User, oldOwner: User)(
      implicit
      realmName: String
  ): Unit = {
    val oldOwnerGroup = oldOwner.getOwnDeviceGroup
    val newOwnerGroup = newOwner.getOwnDeviceGroup
    leaveGroup(oldOwnerGroup)
    joinGroup(newOwnerGroup)
  }

  private def leaveOldGroupsJoinNewGroups(newGroups: List[String], excludedGroups: List[String]): Unit = {
    leaveAllGroupExceptSpecified(excludedGroups)
    joinNewGroups(newGroups)
  }

  private def joinNewGroups(newGroups: List[String]): Unit = {
    newGroups foreach { newGroup =>
      joinGroup(GroupFactory.getByName(newGroup))
    }
  }

  private def leaveAllGroupExceptSpecified(groupToKeep: List[String])(implicit realmName: String): Unit = {
    getAllGroups foreach { group =>
      if (!groupToKeep.contains(group.name)) {
        leaveGroup(group)
      }
    }
  }

  def isUserAuthorized(user: User): DeviceFE = {
    logger.debug(getOwner.toSimpleUser.toString)
    if (getOwner.isEqual(user)) this.toDeviceFE
    else throw PermissionException(s"""Device ${toDeviceStub.toString} does not belong to user ${user.toSimpleUser.toString}""")
  }

  def toDeviceFE: DeviceFE = {
    val representation = toRepresentation
    val deviceHwId = representation.getUsername
    val creationDate = representation.getCreatedTimestamp.toString
    val description = representation.getLastName
    val groups = this.getGroups
    val deviceType = this.getDeviceType
    val customerId = Util.getCustomerId(realmName)
    logger.debug(representation.getAttributes.toString)
    val attributes: Map[String, List[String]] = Converter.attributesToMap(representation.getAttributes)
    DeviceFE(
      id = memberId,
      hwDeviceId = deviceHwId,
      description = description,
      owner = getOwner.toSimpleUser,
      groups = removeUnwantedGroupsFromDeviceStruct(groups),
      attributes = attributes,
      deviceType = deviceType,
      created = creationDate,
      customerId = customerId
    )
  }

  override def getGroups: List[Group] = super.getGroups.filter { group =>
    !(group.name.contains(Elements.PREFIX_DEVICE_TYPE) || group.name.contains(Elements.PREFIX_API) || group.name.contains(Elements.PREFIX_OWN_DEVICES))
  }

  def getOwner: User = {
    val userGroup = getAllGroups.find { group => group.name.contains(Elements.PREFIX_OWN_DEVICES) } match {
      case Some(value) => value
      case None => throw new InternalApiException(s"No owner defined for device $memberId")
    }
    val ownerUserName = userGroup.name.split(Elements.PREFIX_OWN_DEVICES)(
      Elements.OWN_DEVICES_GROUP_USERNAME_PLACE
    )
    UserFactory.getByUsername(ownerUserName)
  }

  private[structure] def removeUnwantedGroupsFromDeviceStruct(
      groups: List[Group]
  ): List[GroupFE] = {
    val filteredGroups = groups.filter { g =>
      !(g.name.contains(Elements.PREFIX_DEVICE_TYPE) || g.name.contains(Elements.PREFIX_API))
    }
    filteredGroups.map(g => g.toGroupFE)
  }

  def getDeviceConfigGroup: Group = getAllGroups.filter(p => p.name.contains(Elements.PREFIX_DEVICE_TYPE)).head

  def toDeviceStub: DeviceStub = {
    DeviceStub(hwDeviceId = getUsername,
      description = getDescription,
      deviceType = getDeviceType
    )
  }

  def getDescription: String = this.getLastName

  def getDeviceType: String = {
    getAllGroups.find { group => group.name.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => group.name.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
      case None => throw new InternalApiException(s"Device with Id $memberId has no type")
    }
  }

  def getDeviceTypeGroup: Group = {
    getAllGroups.find { group => group.name.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => GroupFactory.getById(group.id)
      case None => throw new InternalApiException(s"Device with Id $memberId has no type")
    }
  }

  private[structure] def getAllGroups = super.getGroups

  protected[structure] def deleteDevice(): Unit = deleteMember()

  /**
  * Return the number of UPPs that a device has created during the specified timeframe
    * @return
    */
  def getUPPs(from: Long, to: Long): UppState = {
    implicit val gc: GremlinConnector = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)
    val hwDeviceId = getUsername
    val res = gc.g.V().
      has(Key[String]("device_id"), getUsername).both().
      has(Key[Long]("timestamp"), P.inside(from, to)).
      count().
      l().head.toLong
    UppState(hwDeviceId, from, to, res.toInt)
  }
}


case class UppState(hwDeviceId: String, from: Long, to: Long, numberUpp: Int) {
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = hwDeviceId ->
        ("numberUPPs" -> numberUpp) ~
        ("from" -> from) ~
        ("to" -> to)
    compact(render(json))
  }
}


trait DeviceCreationState {
  def hwDeviceId: String
  def state: String
  def toJson: String
}

case class DeviceCreationSuccess(hwDeviceId: String)
  extends DeviceCreationState {
  def state = "ok"
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = hwDeviceId -> ("state" -> "ok")
    compact(render(json))
  }
}

case class DeviceCreationFail(hwDeviceId: String, error: String, errorCode: Int)
  extends DeviceCreationState {
  def state = "notok"

  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val jsonError =
      hwDeviceId ->
        ("state" -> "notok") ~
        ("error" -> error) ~
        ("errorCode" -> errorCode)
    compact(render(jsonError))
  }
}
