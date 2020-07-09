package com.ubirch.webui.models.keycloak.member

object UserFactory {

  val memberType: MemberType.Value = MemberType.User

  def getByKeyCloakId(kcId: String)(implicit realmName: String): User = MemberFactory.getById(kcId, memberType).asInstanceOf[User]
  def getByUsername(username: String)(implicit realmName: String): User = MemberFactory.getByUsername(username, memberType).asInstanceOf[User]
  def getByAName(description: String)(implicit realmName: String): User = MemberFactory.getByAName(description, memberType).asInstanceOf[User]

}
