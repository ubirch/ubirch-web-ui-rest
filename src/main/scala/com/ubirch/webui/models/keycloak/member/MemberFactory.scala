package com.ubirch.webui.models.keycloak.member

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Exceptions.{ BadRequestException, MemberNotFound }
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.models.keycloak.util.{ QuickActions, Util }
import org.keycloak.admin.client.resource.UserResource

import scala.collection.JavaConverters._

object MemberFactory extends LazyLogging {

  def getByUsername(userName: String, memberType: MemberType)(implicit realmName: String): Member = {
    val user = QuickActions.quickSearchUserNameOnlyOne(userName)
    val keyCloakMember = Util.getRealm.users().get(user.getId)
    returnCorrectMemberType(keyCloakMember, memberType)
  }

  def getByFirstName(name: String, memberType: MemberType, namingConvention: String = "Name")(implicit realmName: String): Member = {
    logger.debug("name: " + name)

    if (name.isEmpty) {
      throw BadRequestException(s"$namingConvention should not be empty")
    } else {
      val memberRepresentation = QuickActions.quickSearchFirstNameStrict(name)
      getById(memberRepresentation.getId, memberType)
    }
  }

  def getByAName(name: String, memberType: MemberType)(implicit realmName: String): Member = {
    logger.debug("name: " + name)
    val memberRepresentation = QuickActions.quickSearchName(name)
    getById(memberRepresentation.getId, memberType)
  }

  def getById(keyCloakId: String, memberType: MemberType)(implicit realmName: String): Member = {
    returnCorrectMemberType(QuickActions.quickSearchId(keyCloakId), memberType)
  }

  private def returnCorrectMemberType(member: UserResource, memberType: MemberType)(implicit realmName: String) = {
    memberType match {
      case MemberType.User => new User(member)
      case MemberType.Device => new Device(member)
    }
  }

  def getMultiple(name: String, memberType: MemberType, size: Int)(implicit realmName: String): List[Member] = {
    val realm = Util.getRealm
    logger.debug("name: " + name)
    val membersAsResource = realm.users().search(name, 0, size) match {
      case null => throw MemberNotFound(s"Member with name $name is not present in $realmName")
      case members =>
        if (members.size() <= size) members.asScala.toList
        else
          throw MemberNotFound(s"More than one member(s) with attribute $name in $realmName")
    }
    membersAsResource.map { member => getById(member.getId, memberType) }.filter(m => m.isMemberOfType(memberType))
  }

  protected[keycloak] def genericBuilderFromId(id: String)(implicit realmName: String): Member = {
    Util.memberType(id) match {
      case MemberType.User => UserFactory.getByKeyCloakId(id)
      case MemberType.Device => DeviceFactory.getByKeyCloakId(id)
    }
  }

}

object MemberType extends Enumeration {
  type MemberType = Value
  val User, Device = Value
}
