package com.ubirch.webui.core

object Exceptions {

  class InternalApiException(message: String) extends Exception(message) {
    val errorCode: Int = 1
  }

  case class PermissionException(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 2
  }

  case class UserNotFound(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 3
  }

  case class GroupNotFound(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 4
  }

  case class DeviceNotFound(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 5
  }

  case class GroupNotEmpty(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 6
  }

  case class BadOwner(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 7
  }

  case class NotAuthorized(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 8
  }

  case class HexDecodingError(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 9
  }

  case class DateTimeParseError(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 10
  }

}
