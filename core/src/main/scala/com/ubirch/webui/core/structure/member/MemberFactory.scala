package com.ubirch.webui.core.structure.member

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.Exceptions.MemberNotFound
import com.ubirch.webui.core.structure.Util
import com.ubirch.webui.core.structure.member.MemberType.MemberType
import org.keycloak.admin.client.resource.UserResource

import scala.collection.JavaConverters._

object MemberFactory extends LazyLogging {

  def getByUsername(userName: String, memberType: MemberType)(implicit realmName: String): Member = {
    val realm = Util.getRealm
    val usersOption = Option(realm.users().search(userName)) match {
      case Some(members) =>
        //logger.debug(s"member with username $userName: ${members.asScala.toList.map(users => users.getUsername)} FN: ${members.asScala.toList.map(users => users.getFirstName)}")
        members.asScala.toList.filter { user =>
          user.getUsername.equalsIgnoreCase(userName)
        }
      case None =>
        throw MemberNotFound(s"member in realm $realmName with username $userName not found")
    }

    val user = usersOption match {
      case u if u.size > 1 =>
        throw MemberNotFound(s"More than one member in realm $realmName has the username $userName")
      case u if u.isEmpty =>
        throw MemberNotFound(s"No member named $userName in the realm $realmName")
      case u => u.head
    }
    val keyCloakMember = realm.users().get(user.getId)
    returnCorrectMemberType(keyCloakMember, memberType)
  }

  def getByFirstNameStrict(name: String, memberType: MemberType)(implicit realmName: String): Member = {
    val _name = name
    val realm = Util.getRealm
    logger.debug("name: " + name)
    val maxResult = 2
    val memberRepresentation = realm.users()
      .search(null, _name, null, null, 0, maxResult, true) match {
        case null =>
          throw MemberNotFound(s"Member with name ${_name} is not present in $realmName")
        case members =>
          members.asScala.toList match {
            case Nil => throw MemberNotFound(s"No members found with name=${_name} in $realmName")
            case List(member) =>
              if (member.getFirstName == _name) member
              else throw MemberNotFound(s"No members found with name=${_name} in $realmName")
            case _ => throw MemberNotFound(s"More than one member(s) with name=${_name} in $realmName")
          }

      }

    getById(memberRepresentation.getId, memberType)
  }

  def getByAName(name: String, memberType: MemberType)(implicit realmName: String): Member = {
    val realm = Util.getRealm
    logger.debug("name: " + name)
    val maxResult = 2
    val memberRepresentation = realm.users().search(name, 0, maxResult) match {
      case null =>
        throw MemberNotFound(s"Member with name $name is not present in $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No members found with name=$name in $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member(s) with name=$name in $realmName")
        }

    }
    getById(memberRepresentation.getId, memberType)
  }

  def getById(keyCloakId: String, memberType: MemberType)(implicit realmName: String): Member = {
    val realm = Util.getRealm
    Option(realm.users().get(keyCloakId)) match {
      case Some(member) => returnCorrectMemberType(member, memberType)
      case None =>
        throw MemberNotFound(
          s"no member in realm $realmName with id $keyCloakId was found"
        )
    }
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
    membersAsResource.map { member => getById(member.getId, memberType) }
  }

  protected[structure] def genericBuilderFromId(id: String)(implicit realmName: String): Member = {
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
