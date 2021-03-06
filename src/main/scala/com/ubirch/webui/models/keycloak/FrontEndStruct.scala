package com.ubirch.webui.models.keycloak

import com.ubirch.webui.models.Elements
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.write

case class GroupFE(id: String, name: String)

case class SimpleUser(id: String, username: String, lastname: String, firstname: String) {
  override def toString: String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    write(this)
  }
}

case class UserAccountInfo(user: SimpleUser, numberOfDevices: Int, isAdmin: Boolean)

case class DeviceFE(
    id: String,
    hwDeviceId: String,
    override val description: String,
    owner: List[SimpleUser],
    groups: List[GroupFE],
    attributes: Map[String, List[String]],
    override val deviceType: String = "default_type",
    created: String = "cc",
    canBeDeleted: Boolean
) extends DeviceBase {
  def addToAttributes(attributesToAdd: Map[String, List[String]]): DeviceFE = copy(attributes = this.attributes ++ attributesToAdd)
  def addPrefixToDescription(pref: String): DeviceFE = copy(description = pref + this.description)
  def removeFromAttributes(attributeToRemove: List[String]): DeviceFE = copy(attributes = this.attributes -- attributeToRemove)
  def addGroup(group: GroupFE): DeviceFE = copy(groups = groups :+ group)
  def removeGroup(group: GroupFE): DeviceFE = copy(groups = groups.filter(n => !n.name.toLowerCase.equals(group.name.toLowerCase)))
}

case class DeviceStub(
    hwDeviceId: String,
    override val description: String,
    override val deviceType: String = "default_type",
    canBeDeleted: Boolean
) extends DeviceBase {
  override def toString: String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    write(this)
  }
}

case class DeviceDumb(
    hwDeviceId: String,
    override val description: String,
    customerId: String,
    owners: List[SimpleUser]
) extends DeviceBase {
  override def toString: String = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    write(this)
  }
}

case class UserInfo(realmName: String, id: String, userName: String)

case class AddDevice(
    hwDeviceId: String,
    override val description: String,
    override val deviceType: String = "default_type",
    listGroups: List[String] = Nil,
    attributes: Map[String, List[String]] = Map.empty,
    secondaryIndex: String = Elements.DEFAULT_FIRST_NAME
) extends DeviceBase {
  def addToAttributes(attributesToAdd: Map[String, List[String]]): AddDevice = copy(attributes = this.attributes ++ attributesToAdd)
  def addPrefixToDescription(pref: String): AddDevice = copy(description = pref + this.description)
  def removeFromAttributes(attributeToRemove: List[String]): AddDevice = copy(attributes = this.attributes -- attributeToRemove)
  def addGroup(groupName: String): AddDevice = copy(listGroups = listGroups :+ groupName)
  def removeGroup(groupName: String): AddDevice = copy(listGroups = listGroups.filter(n => !n.equals(groupName)))
}

abstract class DeviceBase {
  def hwDeviceId: String

  def description: String = ""

  def deviceType: String = "default_type"
}

case class ReturnDeviceStubList(numberOfDevices: Int, devices: List[DeviceStub])

case class BulkRequest(reqType: String, tags: List[String], prefix: Option[String], devices: List[AddDevice])
