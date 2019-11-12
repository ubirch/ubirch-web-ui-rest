package com.ubirch.webui.core.structure.member

object UserFactory {

  val memberType: MemberType.Value = MemberType.User

  def getByKeyCloakId(kcId: String)(implicit realmName: String): User = MemberFactory.getById(kcId, memberType).asInstanceOf[User]
  def getByUsername(hwDeviceId: String)(implicit realmName: String): User = MemberFactory.getByUsername(hwDeviceId, memberType).asInstanceOf[User]
  def getByAName(description: String)(implicit realmName: String): User = MemberFactory.getByAName(description, memberType).asInstanceOf[User]

}
