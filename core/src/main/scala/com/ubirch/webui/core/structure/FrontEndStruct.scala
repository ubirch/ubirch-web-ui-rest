package com.ubirch.webui.core.structure


case class Group(id: String, name: String)

case class User(id: String, username: String, lastname: String, firstname: String)

case class Device(id: String,
                  hwDeviceId: String,
                  description: String,
                  owner: User,
                  groups: List[Group],
                  attributes: Map[String, List[String]])

case class DeviceStubs(hwDeviceId: String, description: String) //TODO: add device type