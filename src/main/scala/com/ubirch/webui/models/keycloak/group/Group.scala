package com.ubirch.webui.models.keycloak.group

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Exceptions.{ BadRequestException, GroupNotEmpty, GroupNotFound, InternalApiException }
import com.ubirch.webui.models.keycloak.{ DeviceStub, GroupFE }
import com.ubirch.webui.models.keycloak.member.{ MemberFactory, Members }
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.keycloak.util.{ Converter, QuickActions, MemberResourceRepresentation }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.keycloak.admin.client.resource.{ GroupResource, UserResource }
import org.keycloak.representations.idm.{ GroupRepresentation, UserRepresentation }

import scala.collection.JavaConverters._
import scala.util.Try

class Group(val keyCloakGroup: GroupResource)(implicit realmName: String) extends LazyLogging {

  import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._

  val maxDeviceQueried = 100000

  /**
    * Return the desired amount of devices with the pagination desired in this user group
    * @param page Number of the page requested. Start at 0.
    * @param pageSize Number of devices returned by page.
    * @return A pageSize number of DeviceStubs. If the number of devices returned is lower, then the end of the device
    *         group has been reached
    */
  def getDevicesPagination(page: Int = 0, pageSize: Int = maxDeviceQueried): List[DeviceStub] = {
    // getting the username in order to see if the devices that we're querying are alphabetically before or after the user
    // If they're after, then a device at the position (page + 1)*pageSize should replace the one at the begining, as it
    // won't be at the correct position
    // Same, if we find the user in the page, pageSize requested devices, we have to remove it from the list and pick
    // the device that is after
    if (page < 0) return Nil
    if (pageSize < 0) throw BadRequestException("page size should not be negative")
    val ownerUsername: String = name.drop(Elements.PREFIX_OWN_DEVICES.length)
    val start = page * pageSize

    val membersInGroupPaginated: List[MemberResourceRepresentation] = getMembersPaginationQuick(start, pageSize).map(m => MemberResourceRepresentation(QuickActions.quickSearchId(m.getId), m))
    val devices: List[MemberResourceRepresentation] = membersInGroupPaginated.filter(member => member.resource.isDevice)

    /**
      * devices should be sorted by hwDeviceIds (ie: username)
      */
    def areDevicesQueriedAlphabeticallyAfterTheUserQuick(devices: List[MemberResourceRepresentation]) = {
      devices.head.representation.getUsername > ownerUsername
    }

    /**
      * Simply verify that the devices list is smaller than the membersInGroupPaginated list. If that's the case, then the user was inside
      */
    def isUserInQueriedDevices = membersInGroupPaginated.size > devices.size

    def getDeviceAtPosition(position: Int): Option[MemberResourceRepresentation] = Try(getMembersPaginationQuick(position, 1).map(m => QuickActions.quickSearchId(m.getId)).filter(member => member.isDevice).map(d => MemberResourceRepresentation(d, d.toRepresentation)).head).toOption

    /**
      * If a device exist at the given position, add it to the devices. Otherwise, return the devices
      */
    def maybeAddDeviceQuick(devices: List[MemberResourceRepresentation]): List[MemberResourceRepresentation] = {
      val maybeDevice = getDeviceAtPosition((page + 1) * pageSize)
      maybeDevice match {
        case Some(d) => devices :+ d
        case None => devices
      }
    }

    if (membersInGroupPaginated.isEmpty) {
      Nil
    } else {
      val correctDevices = if (isUserInQueriedDevices) {
        maybeAddDeviceQuick(devices)
      } else if (areDevicesQueriedAlphabeticallyAfterTheUserQuick(devices)) maybeAddDeviceQuick(devices.tail) else devices

      correctDevices.sortBy(_.representation.getUsername) map (d => DeviceStub(
        hwDeviceId = d.representation.getUsername,
        description = d.representation.getLastName,
        deviceType = d.getType
      ))
    }

  }

  /**
    * Get all the members of a group. Returns a maximum of 100 individuals
    * @return A maximum of 100 users in a group
    */
  def getMembers = Members(keyCloakGroup.members().asScala.toList map { m => MemberFactory.genericBuilderFromId(m.getId) })

  def getMembersPaginationQuick(start: Int, size: Int): List[UserRepresentation] = keyCloakGroup.members(start, size).asScala.toList

  def getMaxCount(maxCount: Int = Int.MaxValue): Int = keyCloakGroup.members(0, maxCount).size()

  def getAttributes = GroupAttributes(getRepresentation.getAttributes.asScala.toMap)

  def getRepresentation: GroupRepresentation = keyCloakGroup.toRepresentation

  def getUpdatedGroup: Group = GroupFactory.getById(id)

  lazy val id: String = keyCloakGroup.toRepresentation.getId

  def toGroupFE: GroupFE = {
    val representation = keyCloakGroup.toRepresentation
    GroupFE(representation.getId, representation.getName)
  }

  def deleteGroup(): Unit = {

    if (id == null || id == "") throw GroupNotFound(s"Group doesn't exist")

    if (!isEmpty) throw GroupNotEmpty(s"Group with id $id is not empty")
    if (name.contains(Elements.PREFIX_OWN_DEVICES))
      throw new InternalApiException(
        s"Group with id $id is a user group with name $name"
      )
    else {
      keyCloakGroup.remove()
    }
  }

  def name: String = keyCloakGroup.toRepresentation.getName

  def isEmpty: Boolean = numberOfMembers == 0

  /**
    * Only returns a maximum of 100 for speed reasons
    */
  def numberOfMembers: Int = keyCloakGroup.members().size()
}

case class GroupAttributes(attributes: Map[String, util.List[String]]) {
  def getValue(key: String): String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    val json = parse(attributes.head._2.asScala.head)
    (json \ key).extract[String]
  }
  def asScala: Map[String, List[String]] = Converter.attributesToMap(attributes.asJava)
}

//
//
//object Test {
//  implicit val realmName: String = ""
//  val g: Group = null
//  val membersInGroupPaginated: List[ResourceRepresentation] = g.getMembersPaginationQuick(0, 100).map(m => ResourceRepresentation(QuickActions.quickSearchId(m.getId), m))
//  val devices: List[ResourceRepresentation] = membersInGroupPaginated.filter(member => member.resource.roles().realmLevel().listEffective().asScala.toList.exists {
//    m => m.getName.equalsIgnoreCase(Elements.DEVICE)
//  })
//
//  devices.head.resource
//
//  // TODO: check how I can bring implicit class to use in  another class
//  implicit class RichUserResource(val userResource: UserResource) extends AnyVal {
//    def truc = {
//      ""
//    }
//  }
//
//}

