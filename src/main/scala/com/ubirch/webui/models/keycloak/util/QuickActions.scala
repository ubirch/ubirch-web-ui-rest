package com.ubirch.webui.models.keycloak.util

import com.ubirch.webui.models.Exceptions.MemberNotFound
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters._

/**
  * Methods here are "quick" in the sense as they are not casted to a full type like a User or Device.
  * They are meant to be used as quick verification of existence.
  */
object QuickActions {

  def quickSearchFirstNameStrict(firstName: String, briefRepresentation: Boolean = false)(implicit realmName: String): UserRepresentation = {
    //TODD: Research this: This parameter is not used as it causes a runtime error
    val _ = briefRepresentation
    //

    val realm = Util.getRealm

    val maxResult = 2
    // username, firstName, lastName, email, firstResult, maxResult, briefRepresentation
    realm.users().search(null, firstName, null, null, 0, maxResult) match {
      case null =>
        throw MemberNotFound(s"Member with name $firstName is not present in $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No members found with $firstName in $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member(s) with $firstName in $realmName")
        }
    }
  }

  def quickSearchName(aName: String)(implicit realmName: String): UserRepresentation = {
    val realm = Util.getRealm

    val maxResult = 2
    realm.users().search(aName, 0, maxResult) match {
      case null =>
        throw MemberNotFound(s"Member with name $aName is not present in $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No members found with name=$aName in $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member(s) with name=$aName in $realmName")
        }
    }
  }

  /**
    * Will search for a member whose username is the given one. It'll fail if it finds more than one
    * IMPORTANT the search will not be strict !
    * If two users are name
    * 123
    * 12
    * then searching for one of them will FAIL
    * Use quickSearchUserNameStrict to avoid this issue
    * @param userName
    * @param realmName
    * @return
    */
  def quickSearchUserNameOnlyOne(userName: String)(implicit realmName: String): UserRepresentation = {
    val realm = Util.getRealm
    realm.users().search(userName) match {
      case null =>
        throw MemberNotFound(s"No member named $userName in the realm $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No member named $userName in the realm $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member in realm $realmName has the username $userName")
        }
    }
  }

  def quickSearchUserNameGetAll(userName: String)(implicit realmName: String): List[UserRepresentation] = {
    val realm = Util.getRealm
    realm.users().search(userName, 0, 100) match {
      case null =>
        throw MemberNotFound(s"No member named $userName in the realm $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No member named $userName in the realm $realmName")
          case x => x
        }
    }
  }

  def quickSearchId(keyCloakId: String)(implicit realmName: String): UserResource = {
    val realm = Util.getRealm
    realm.users().get(keyCloakId) match {
      case null => throw MemberNotFound(s"no member in realm $realmName with id $keyCloakId was found")
      case x => x
    }
  }
}
