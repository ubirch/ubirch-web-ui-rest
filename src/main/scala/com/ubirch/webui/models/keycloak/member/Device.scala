package com.ubirch.webui.models.keycloak.member

import java.util.Date

import com.ubirch.webui.models.Exceptions.{ DeviceAlreadyClaimedException, InternalApiException, PermissionException }
import com.ubirch.webui.models.keycloak._
import com.ubirch.webui.models.keycloak.group.{ Group, GroupFactory }
import com.ubirch.webui.models.keycloak.util.{ Converter, QuickActions, Util }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.services.connector.janusgraph.{ ConnectorType, GremlinConnector, GremlinConnectorFactory }
import gremlin.scala.{ Key, P }
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.{ GroupRepresentation, UserRepresentation }

import scala.collection.JavaConverters._
import scala.util.Try

class Device(keyCloakMember: UserResource)(implicit realmName: String) extends Member(keyCloakMember) {

  def getHwDeviceId: String = this.getUsername

  def getSecondaryIndex: String = this.getFirstName

  def updateDevice(deviceUpdateStruct: DeviceFE): Device = {
    val deviceRepresentation = toRepresentation
    deviceRepresentation.setLastName(deviceUpdateStruct.description)

    val newDeviceTypeGroup = GroupFactory.getByName(Util.getDeviceConfigGroupName(deviceUpdateStruct.deviceType))

    val newOwners = deviceUpdateStruct.owner.map(o => UserFactory.getByKeyCloakId(o.id))
    changeOwnersOfDevice(newOwners)

    deviceRepresentation.setAttributes(deviceUpdateStruct.attributes.map { kv => kv._1 -> kv._2.asJava }.asJava)

    keyCloakMember.update(deviceRepresentation)

    val ownersGroups = getOwners.map { u => u.getOwnDeviceGroup.name }
    val excludedGroupNames: List[String] = ownersGroups :+ getApiConfigGroup.name
    leaveOldGroupsJoinNewGroups(deviceUpdateStruct.groups.map(_.name) :+ newDeviceTypeGroup.name, excludedGroupNames)
    getUpdatedDevice
  }

  def getApiConfigGroup: Group =
    getAllGroups.filter(p => p.name.contains(Elements.PREFIX_API)).head

  def getUpdatedDevice: Device = DeviceFactory.getByKeyCloakId(memberId)

  protected[keycloak] def changeOwnersOfDevice(newOwners: List[User]): Unit = {

    if (newOwners.isEmpty) throw new InternalApiException("new owner list can not be empty")

    val oldOwners = Try(getOwners).getOrElse(Nil)

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

  private def leaveAllGroupExceptSpecified(groupToKeep: List[String]): Unit = {
    getAllGroups foreach { group =>
      if (!groupToKeep.contains(group.name)) {
        leaveGroup(group)
      }
    }
  }

  def ifUserAuthorizedReturnDeviceFE(user: User): DeviceFE = {
    val owners = getOwnersQuick()
    logger.debug("owners: " + owners.map { u => u.getUsername }.mkString(", "))
    if (owners.exists(u => u.getId.equalsIgnoreCase(user.memberId))) this.toDeviceFE
    else throw PermissionException(s"""Device ${toDeviceStub.toString} does not belong to user ${user.toSimpleUser.toString}""")
  }

  def isUserAuthorized(user: User): Boolean = {
    getOwnersQuick().exists(u => u.getId.equalsIgnoreCase(user.memberId))
  }

  def toDeviceFE: DeviceFE = {

    val t0 = System.currentTimeMillis()
    val representation = toRepresentation
    var t1 = System.currentTimeMillis()
    logger.debug(s"~~~ Time to toRepresentation = ${System.currentTimeMillis() - t1}ms")

    val deviceHwId = representation.getUsername
    val creationDate = representation.getCreatedTimestamp.toString
    val description = representation.getLastName
    t1 = System.currentTimeMillis()
    val allGroupsRepresentation = getAllGroupsQuick
    logger.debug(s"~~~ Time to getAllGroups = ${System.currentTimeMillis() - t1}ms")

    t1 = System.currentTimeMillis()
    val groupsWithoutUnwantedOnes = allGroupsRepresentation
      .filter { group => !(group.getName.contains(Elements.PREFIX_DEVICE_TYPE) || group.getName.contains(Elements.PREFIX_API) || group.getName.contains(Elements.PREFIX_OWN_DEVICES)) }
      .map { representation => GroupFE(representation.getId, representation.getName) }
    logger.debug(s"~~~ Time to getPartialGroups = ${System.currentTimeMillis() - t1}ms")

    t1 = System.currentTimeMillis()
    val deviceType = allGroupsRepresentation find { group => group.getName.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => group.getName.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
      case None => throw new InternalApiException(s"Device with Id $memberId has no type")
    }
    logger.debug(s"~~~ Time to getDeviceType = ${System.currentTimeMillis() - t1}ms")

    t1 = System.currentTimeMillis()
    val attributes: Map[String, List[String]] = Converter.attributesToMap(representation.getAttributes)
    logger.debug(s"~~~ Time to attributes = ${System.currentTimeMillis() - t1}ms")

    t1 = System.currentTimeMillis()
    val owners = Try {
      val ownerGroups = allGroupsRepresentation
        .filter { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) }
        .map { group => group.getName.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }
      if (ownerGroups.isEmpty) {
        Nil
      } else {
        ownerGroups map { username =>
          val userRepresentation = QuickActions.quickSearchUserNameOnlyOne(username)
          SimpleUser(
            userRepresentation.getId,
            userRepresentation.getUsername,
            userRepresentation.getLastName,
            userRepresentation.getFirstName
          )
        }
      }
    }
    logger.debug(s"~~~ Time to get owners = ${System.currentTimeMillis() - t1}ms")

    t1 = System.currentTimeMillis()
    val res = DeviceFE(
      id = memberId,
      hwDeviceId = deviceHwId,
      description = description,
      owner = owners.getOrElse(Nil),
      groups = groupsWithoutUnwantedOnes,
      attributes = attributes,
      deviceType = deviceType,
      created = creationDate
    )
    logger.debug(s"~~~ Time to deviceFe = ${System.currentTimeMillis() - t1}ms")
    logger.debug(s"~~ Time to toDeviceFE = ${System.currentTimeMillis() - t0}ms")
    res
  }

  def toAddDevice: AddDevice = {
    val deviceFE = toDeviceFE
    AddDevice(
      hwDeviceId = deviceFE.hwDeviceId,
      description = deviceFE.description,
      deviceType = deviceFE.deviceType,
      listGroups = this.getAllGroups.map { g => g.name },
      attributes = deviceFE.attributes,
      secondaryIndex = this.getSecondaryIndex
    )
  }

  /**
    * Check if a device belongs to a user_OWN_DEVICES group
    */
  def isClaimed: Boolean = getAllGroups.exists(g => g.name.toLowerCase.contains(Elements.PREFIX_OWN_DEVICES.toLowerCase))

  def getPartialGroups: List[Group] = super.getGroups.filter { group =>
    !(group.name.contains(Elements.PREFIX_DEVICE_TYPE) || group.name.contains(Elements.PREFIX_API) || group.name.contains(Elements.PREFIX_OWN_DEVICES))
  }

  def getOwners: List[User] = {
    val usersGroups = getAllGroups.filter { group => group.name.contains(Elements.PREFIX_OWN_DEVICES) }
    if (usersGroups.isEmpty) throw new InternalApiException(s"No owner defined for device $memberId")
    val ownersUserNames = usersGroups.map { g => g.name.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }

    ownersUserNames map { username => UserFactory.getByUsername(username) }

  }

  def getOwnersQuick(): List[UserRepresentation] = {
    val ownerGroups = getAllGroupsQuick
      .filter { group => group.getName.contains(Elements.PREFIX_OWN_DEVICES) }
      .map { group => group.getName.split(Elements.PREFIX_OWN_DEVICES)(Elements.OWN_DEVICES_GROUP_USERNAME_PLACE) }
    if (ownerGroups.isEmpty) {
      Nil
    } else {
      ownerGroups map { username => QuickActions.quickSearchUserNameOnlyOne(username) }
    }
  }

  private[keycloak] def removeUnwantedGroupsFromDeviceStruct(groups: List[Group]): List[GroupFE] = {
    val filteredGroups = groups.filter { g =>
      !(g.name.contains(Elements.PREFIX_DEVICE_TYPE) || g.name.contains(Elements.PREFIX_API))
    }
    filteredGroups.map(g => g.toGroupFE)
  }

  def getDeviceConfigGroup: Group = getAllGroups.filter(p => p.name.contains(Elements.PREFIX_DEVICE_TYPE)).head

  def toDeviceStub: DeviceStub = {
    val representation = toRepresentation
    DeviceStub(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      deviceType = getDeviceType
    )
  }

  def toDeviceDumb: DeviceDumb = {
    val t0 = System.currentTimeMillis()
    val representation: UserRepresentation = toRepresentation
    val deviceDumb = DeviceDumb(
      hwDeviceId = representation.getUsername,
      description = representation.getLastName,
      customerId = Util.getCustomerId(realmName)
    )
    logger.debug(s"~~~ Time to deviceDumb = ${System.currentTimeMillis() - t0}ms")
    deviceDumb
  }

  def getDescription: String = this.getLastName

  def getDeviceType: String = {
    getAllGroupsQuick find { group => group.getName.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => group.getName.split(Elements.PREFIX_DEVICE_TYPE)(Elements.DEVICE_TYPE_TYPE_PLACE)
      case None => throw new InternalApiException(s"Device with Id $memberId has no type")
    }
  }

  def getDeviceTypeGroup: Group = {
    getAllGroups.find { group => group.name.contains(Elements.PREFIX_DEVICE_TYPE) } match {
      case Some(group) => GroupFactory.getById(group.id)
      case None => throw new InternalApiException(s"Device with Id $memberId has no type")
    }
  }

  private[keycloak] def getAllGroups: List[Group] = super.getGroups

  private[keycloak] def getAllGroupsQuick: List[GroupRepresentation] = keyCloakMember.groups().asScala.toList

  protected[keycloak] def deleteDevice(): Unit = deleteMember()

  /**
    * Return the number of UPPs that a device has created during the specified timeframe
    * @return
    */
  def getUPPs(from: Long, to: Long): UppState = {
    implicit val gc: GremlinConnector = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)

    val hwDeviceId = getHwDeviceId

    val res = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .inE("UPP->DEVICE")
      .has(Key[Date]("timestamp"), P.inside(convertToDate(from), convertToDate(to)))
      .count()
      .l().head.toLong

    UppState(hwDeviceId, from, to, res.toInt)
  }

  /**
    * Will query the graph backend to find the last-hash property value contained on the graph
    * If it is not found, will return a failed LastHash structure
    * @return a LastHash object containing the last hash (if found).
    */
  def getLastHash: LastHash = {
    implicit val gc: GremlinConnector = GremlinConnectorFactory.getInstance(ConnectorType.JanusGraph)

    val hwDeviceId = getHwDeviceId

    val gremlinQueryResult = gc.g.V().has(Key[String]("device_id"), hwDeviceId)
      .value(Key[String]("last_hash")).l().headOption

    LastHash(hwDeviceId, gremlinQueryResult)
  }

  def convertToDate(dateAsLong: Long) = new java.util.Date(dateAsLong)

  def stopIfDeviceAlreadyClaimed(): Unit = if (this.isClaimed) throw DeviceAlreadyClaimedException(s"Device already claimed by ${this.getOwners.map(_.getUsername).mkString(", ")}")

  def getProviderName: String = {
    this.getAllGroups
      .find(p => p.name.contains(Elements.PROVIDER_GROUP_PREFIX))
      .map(_.name)
      .getOrElse("")
      .replace(Elements.PROVIDER_GROUP_PREFIX, "")
  }

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

case class LastHash(hwDeviceId: String, maybeHash: Option[String]) {
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._
  override def toString: String = compact(render(toJson))

  def toJson = {
    maybeHash match {
      case Some(hash) =>
        ("deviceId" -> hwDeviceId) ~
          ("found" -> true) ~
          ("hash" -> hash)
      case None =>
        ("deviceId" -> hwDeviceId) ~
          ("found" -> false) ~
          ("hash" -> "")
    }
  }
}

sealed trait DeviceCreationState {
  def hwDeviceId: String
  def state: String
  def toJson: String
}

case class DeviceCreationSuccess(hwDeviceId: String)
  extends DeviceCreationState {
  val state = "ok"
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = hwDeviceId -> ("state" -> "ok")
    compact(render(json))
  }
}

case class DeviceCreationFail(hwDeviceId: String, error: String, errorCode: Int)
  extends DeviceCreationState {
  val state = "notok"

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
