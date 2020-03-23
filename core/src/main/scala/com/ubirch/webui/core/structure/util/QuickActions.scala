package com.ubirch.webui.core.structure.util

import com.ubirch.webui.core.Exceptions.MemberNotFound
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters._

/**
  * Methods here are "quick" in the sense as they are not casted to a full type like a User or Device.
  * They are meant to be used as quick verification of existence.
  */
object QuickActions {

  def quickSearchFirstNameStrict(firstName: String)(implicit realmName: String): UserRepresentation = {
    val realm = Util.getRealm

    val maxResult = 2
    realm.users().search(null, firstName, null, null, 0, maxResult, true) match {
      case null =>
        throw MemberNotFound(s"Member with name $firstName is not present in $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No members found with name=$firstName in $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member(s) with name=$firstName in $realmName")
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

  def quickSearchUserName(userName: String)(implicit realmName: String): UserRepresentation = {
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

  def quickSearchId(keyCloakId: String)(implicit realmName: String): UserResource = {
    val realm = Util.getRealm
    realm.users().get(keyCloakId) match {
      case null => throw MemberNotFound(s"no member in realm $realmName with id $keyCloakId was found")
      case x => x
    }
  }
}
