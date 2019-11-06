package com.ubirch.webui.core.structure.member

case class Members(members: List[Member]) {

  def getDevices: List[Device] = members.filter(m => m.isMemberDevice).asInstanceOf[List[Device]]
  def getUsers: List[User] = members.filter(m => !m.isMemberDevice).asInstanceOf[List[User]]

  def size: Int = members.size
}
