package com.ubirch.webui.core.structure

case class Group(id: String, name: String)

case class User(id: String, username: String, lastname: String, firstname: String)

case class Device(
    id: String,
    hwDeviceId: String,
    override val description: String,
    owner: User,
    groups: List[Group],
    attributes: Map[String, List[String]],
    override val deviceType: String = "default_type"
) extends DeviceBase

case class DeviceStubs(
    hwDeviceId: String,
    override val description: String,
    override val deviceType: String = "default_type"
) extends DeviceBase

case class UserInfo(realmName: String, id: String, userName: String)

case class AddDevice(hwDeviceId: String, override val description: String, override val deviceType: String = "default_type", listGroups: List[String] = Nil) extends DeviceBase

abstract class DeviceBase {
  def hwDeviceId: String

  def description: String = ""

  def deviceType: String = "default_type"
}
