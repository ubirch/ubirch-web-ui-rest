package com.ubirch.webui.models.keycloak.member

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.Exceptions.{ BadRequestException, MemberNotFound }
import com.ubirch.webui.models.keycloak.util.{ QuickActions, Util }
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters._

object MemberFactory extends LazyLogging {

  def getByFirstName(name: String, namingConvention: String = "Name")(implicit realmName: String): UserRepresentation = {
    logger.debug("name: " + name)

    if (name.isEmpty) {
      throw BadRequestException(s"$namingConvention should not be empty")
    } else {
      QuickActions.quickSearchFirstNameStrict(name)
    }
  }

  def getMultiple(name: String, size: Int)(implicit realmName: String): List[UserRepresentation] = {
    val realm = Util.getRealm
    logger.debug("name: " + name)
    val membersAsRepresentation = realm.users().search(name, 0, size) match {
      case null => throw MemberNotFound(s"Member with name $name is not present in $realmName")
      case members =>
        if (members.size() <= size) members.asScala.toList
        else
          throw MemberNotFound(s"More than one member(s) with attribute $name in $realmName")
    }
    membersAsRepresentation
  }

}

object MemberType extends Enumeration {
  type MemberType = Value
  val User, Device = Value
}
