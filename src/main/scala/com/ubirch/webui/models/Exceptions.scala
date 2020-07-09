package com.ubirch.webui.models

object Exceptions {

  class InternalApiException(message: String) extends Exception(message) {
    val errorCode: Int = 1
  }

  case class PermissionException(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 2
  }

  case class MemberNotFound(message: String) extends InternalApiException(message) {
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

  case class DeviceAlreadyClaimedException(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 10
  }

  case class BadRequestException(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 11
  }

  case class AttributesNotFound(message: String) extends InternalApiException(message) {
    override val errorCode: Int = 12
  }

}
