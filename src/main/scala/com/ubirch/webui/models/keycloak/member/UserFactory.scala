package com.ubirch.webui.models.keycloak.member

import com.ubirch.webui.models.keycloak.util.{ MemberResourceRepresentation, QuickActions, Util }

object UserFactory {

  val memberType: MemberType.Value = MemberType.User

  def getByKeyCloakId(kcId: String)(implicit realmName: String): User = MemberFactory.getById(kcId, memberType).asInstanceOf[User]
  def getByUsername(username: String)(implicit realmName: String): User = MemberFactory.getByUsername(username, memberType).asInstanceOf[User]
  def getByAName(description: String)(implicit realmName: String): User = MemberFactory.getByAName(description, memberType).asInstanceOf[User]
  def getByUsernameQuick(username: String)(implicit realmName: String): MemberResourceRepresentation = {
    val representation = QuickActions.quickSearchUserNameOnlyOne(username)
    val resource = Util.getRealm.users().get(representation.getId)
    MemberResourceRepresentation(resource, representation)
  }

}
