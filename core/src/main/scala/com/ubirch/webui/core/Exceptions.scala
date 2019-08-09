package com.ubirch.webui.core

object Exceptions {
  case class UserNotFound(message: String) extends Exception(message)

  case class GroupNotFound(message: String) extends Exception(message)

  case class DeviceNotFound(message: String) extends Exception(message)

  case class GroupNotEmpty(message: String) extends Exception(message)

  case class BadOwner(message: String) extends Exception(message)
}
