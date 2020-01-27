package com.ubirch.webui.core.structure.member

import java.util.Date

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

  def updateDevice(newOwners: List[User], deviceUpdateStruct: AddDevice, deviceConfig: String, apiConfig: String): Device = {
    val deviceRepresentation = toRepresentation
    deviceRepresentation.setLastName(deviceUpdateStruct.description)

    val newDeviceTypeGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(deviceUpdateStruct.deviceType))

    changeOwnersOfDevice(newOwners)

    val deviceAttributes = Map(
      Elements.ATTRIBUTES_DEVICE_GROUP_NAME -> List(deviceConfig).asJava,
      Elements.ATTRIBUTES_API_GROUP_NAME -> List(apiConfig).asJava
    ).asJava

    deviceRepresentation.setAttributes(deviceAttributes)

    keyCloakMember.update(deviceRepresentation)

    val ownersGroups = getOwners.map { u => u.getOwnDeviceGroup.name }
    val excludedGroupNames: List[String] = ownersGroups :+ getApiConfigGroup.name
    leaveOldGroupsJoinNewGroups(deviceUpdateStruct.listGroups :+ newDeviceTypeGroup.name, excludedGroupNames)
    getUpdatedDevice
  }

  def getApiConfigGroup: Group =
    getAllGroups.filter(p => p.name.contains(Elements.PREFIX_API)).head

  def getUpdatedDevice: Device = DeviceFactory.getByKeyCloakId(memberId)

  protected[structure] def changeOwnersOfDevice(newOwners: List[User])(implicit realmName: String): Unit = {

    if (newOwners.isEmpty) throw new InternalApiException("new owner list can not be empty")

    val oldOwners = getOwners

    val ownersThatStay = newOwners.intersect(oldOwners)
    val ownersToRemove = oldOwners.filter(u => !ownersThatStay.contains(u))
    val ownersToAdd = newOwners.filter(u => !ownersThatStay.contains(u))

    logger.debug("owners that stay : " + ownersThatStay.map(u => u.getUsername))

    ownersToRemove foreach { u => leaveGroup(u.getOwnDeviceGroup) }
    ownersToAdd foreach { u => joinGroup(u.getOwnDeviceGroup) }

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
    logger.debug("owners: " + getOwners.map { u => u.toSimpleUser.toString }.mkString(", "))
    if (getOwners.exists(u => u.isEqual(user))) this.toDeviceFE
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
      owner = getOwners.map { owner => owner.toSimpleUser },
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

  def getOwners: List[User] = {
    val usersGroups = getAllGroups.filter { group => group.name.contains(Elements.PREFIX_OWN_DEVICES) }
    if (usersGroups.isEmpty) throw new InternalApiException(s"No owner defined for device $memberId")
    val ownersUserNames = usersGroups.map { g => g.name.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }

    ownersUserNames map { username => UserFactory.getByUsername(username) }

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
    DeviceStub(
      hwDeviceId = getUsername,
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

    val hwDeviceId = getHwDeviceId

    val res = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .inE("device-upp")
      .has(Key[Date]("timestamp"), P.inside(convertToDate(from), convertToDate(to)))
      .count()
      .l().head.toLong

    UppState(hwDeviceId, from, to, res.toInt)
  }

  def convertToDate(dateAsLong: Long) = new java.util.Date(dateAsLong)

}

case class UppState(hwDeviceId: String, from: Long, to: Long, numberUpp: Int) {
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = ("deviceId" -> hwDeviceId) ~
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
