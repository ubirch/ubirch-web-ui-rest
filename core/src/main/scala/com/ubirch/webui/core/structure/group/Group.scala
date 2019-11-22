package com.ubirch.webui.core.structure.group

import java.util

import com.ubirch.webui.core.Exceptions.{ GroupNotEmpty, GroupNotFound, InternalApiException }
import com.ubirch.webui.core.structure.{ DeviceStub, Elements, GroupFE }
import com.ubirch.webui.core.structure.member.{ Device, MemberFactory, Members }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._

class Group(val keyCloakGroup: GroupResource)(implicit realmName: String) {

  def getDevicesPagination(page: Int = 0, pageSize: Int = 1000000): List[DeviceStub] = {
    val members = getMembers
    val sortedMembers: List[Device] = members.getDevices.sortBy(d => d.getUsername)
    val paginizedMembers = try {
      sortedMembers.grouped(pageSize).toList(page)
    } catch {
      case _: IndexOutOfBoundsException => List.empty
    }
    paginizedMembers map { d => d.toDeviceStub }
  }

  def getMembers = Members(keyCloakGroup.members().asScala.toList map { m => MemberFactory.genericBuilderFromId(m.getId) })

  def getAttributes = GroupAttributes(getRepresentation.getAttributes.asScala.toMap)

  def getRepresentation: GroupRepresentation = keyCloakGroup.toRepresentation

  def getUpdatedGroup: Group = GroupFactory.getById(id)

  def id: String = keyCloakGroup.toRepresentation.getId

  def toGroupFE: GroupFE = GroupFE(id, name)

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

  def numberOfMembers: Int = keyCloakGroup.members().size()
}

case class GroupAttributes(attributes: Map[String, util.List[String]]) {
  def getValue(key: String): String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    val json = parse(attributes.head._2.asScala.head)
    (json \ key).extract[String]
  }
}
