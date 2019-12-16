package com.ubirch.webui.core.structure

import org.json4s.jackson.Serialization.write
import org.json4s.DefaultFormats

case class GroupFE(id: String, name: String)

case class SimpleUser(id: String, username: String, lastname: String, firstname: String) {
  override def toString: String = {
    implicit val formats = DefaultFormats
    write(this)
  }
}

case class UserAccountInfo(user: SimpleUser, numberOfDevices: Int)

case class DeviceFE(
    id: String,
    hwDeviceId: String,
    override val description: String,
    owner: List[SimpleUser],
    groups: List[GroupFE],
    attributes: Map[String, List[String]],
    override val deviceType: String = "default_type",
    created: String = "cc",
    customerId: String
) extends DeviceBase

case class DeviceStub(
    hwDeviceId: String,
    override val description: String,
    override val deviceType: String = "default_type"
) extends DeviceBase {
  override def toString: String = {
    implicit val formats = DefaultFormats
    write(this)
  }
}

case class UserInfo(realmName: String, id: String, userName: String)

case class AddDevice(hwDeviceId: String, override val description: String, override val deviceType: String = "default_type", listGroups: List[String] = Nil) extends DeviceBase

abstract class DeviceBase {
  def hwDeviceId: String

  def description: String = ""

  def deviceType: String = "default_type"
}

case class ReturnDeviceStubList(numberOfDevices: Int, devices: List[DeviceStub])
