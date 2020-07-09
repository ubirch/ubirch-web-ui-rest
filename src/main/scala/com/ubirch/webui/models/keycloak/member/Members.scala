package com.ubirch.webui.models.keycloak.member

case class Members(members: List[Member]) {

  def getDevices: List[Device] = members.filter(m => m.isMemberDevice).asInstanceOf[List[Device]]
  def getUsers: List[User] = members.filter(m => !m.isMemberDevice).asInstanceOf[List[User]]

  override def toString: String = members.map { m => m.getUsername }.sorted.mkString(", ")

  def size: Int = members.size
}
