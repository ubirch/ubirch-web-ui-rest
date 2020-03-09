package com.ubirch.webui.core.structure.util

import com.ubirch.webui.core.Exceptions.MemberNotFound
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters._

/**
  * Methods here are "quick" in the sense as they are not casted to a full type like a User or Device.
  * They are meant to be used as quick verification of existence.
  */
object QuickActions {

  def quickSearchFirstName(name: String)(implicit realmName: String): UserRepresentation = {
    val realm = Util.getRealm

    val maxResult = 2
    realm.users().search(name, 0, maxResult) match {
      case null =>
        throw MemberNotFound(s"Member with name $name is not present in $realmName")
      case members =>
        members.asScala.toList match {
          case Nil => throw MemberNotFound(s"No members found with name=$name in $realmName")
          case List(x) => x
          case _ => throw MemberNotFound(s"More than one member(s) with name=$name in $realmName")
        }
    }
  }
}
