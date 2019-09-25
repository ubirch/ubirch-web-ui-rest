package com.ubirch.webui.core

object Exceptions {

  class InternalApiException(message: String) extends Exception(message)

  case class PermissionException(message: String) extends InternalApiException(message)

  case class UserNotFound(message: String) extends InternalApiException(message)

  case class GroupNotFound(message: String) extends InternalApiException(message)

  case class DeviceNotFound(message: String) extends InternalApiException(message)

  case class GroupNotEmpty(message: String) extends InternalApiException(message)

  case class BadOwner(message: String) extends InternalApiException(message)

  case class NotAuthorized(message: String) extends InternalApiException(message)
}
