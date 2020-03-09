package com.ubirch.webui.core.structure.member

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.InternalApiException
import com.ubirch.webui.core.structure.Elements
import com.ubirch.webui.core.structure.group.Group
import com.ubirch.webui.core.structure.util.{ Converter, Util }
import org.keycloak.admin.client.resource.{ RealmResource, UserResource }
import org.keycloak.representations.idm.{ RoleRepresentation, UserRepresentation }

import scala.collection.JavaConverters._

abstract class Member(var keyCloakMember: UserResource)(
    implicit
    realmName: String
) extends LazyLogging {

  implicit val realm: RealmResource = Util.getRealm
  lazy val memberId: String = toRepresentation.getId

  def getUsername: String = toRepresentation.getUsername
  def getFirstName: String = toRepresentation.getFirstName
  def getLastName: String = toRepresentation.getLastName

  def toRepresentation: UserRepresentation = keyCloakMember.toRepresentation

  def isEqual(that: Member): Boolean = memberId.equalsIgnoreCase(that.memberId)

  def getAttributes: Map[String, List[String]] = Converter.attributesToMap(toRepresentation.getAttributes)

  def getGroups: List[Group] =
    Converter.groupsRepresentationToGroup(keyCloakMember.groups().asScala.toList)

  def getRoles: List[RoleRepresentation] =
    keyCloakMember.roles().realmLevel().listAll().asScala.toList

  def isAdmin: Boolean =
    getRoles.exists(_.getName == Elements.ADMIN)

  def addRole(role: RoleRepresentation): Unit = {
    val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
    roleRepresentationList.add(role)
    keyCloakMember.roles().realmLevel().add(roleRepresentationList)
  }

  def leaveGroup(group: Group): Unit = {
    if (isMemberPartOfGroup(group)) {
      try {
        keyCloakMember.leaveGroup(group.id)
      } catch {
        case e: Exception => throw e
      }
    } else {
      throw new InternalApiException(
        s"User with id $memberId is not part of the group with id ${group.id}"
      )
    }
  }

  private def isMemberPartOfGroup(group: Group): Boolean = {
    this.getGroups.exists(g => g.name.equalsIgnoreCase(group.name))
  }

  def joinGroup(group: Group): Unit = joinGroup(group.id)

  def joinGroup(groupId: String): Unit = keyCloakMember.joinGroup(groupId)

  def isMemberDevice: Boolean =
    keyCloakMember.roles().realmLevel().listEffective().asScala.toList.exists {
      m =>
        m.getName.equalsIgnoreCase(Elements.DEVICE)
    }

  protected[structure] def deleteMember(): Unit =
    realm.users().get(memberId).remove()

}
