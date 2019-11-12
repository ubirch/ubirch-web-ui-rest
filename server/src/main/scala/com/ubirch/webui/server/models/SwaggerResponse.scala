package com.ubirch.webui.server.models

import org.json4s.DefaultFormats
import org.scalatra.swagger.ResponseMessage

object SwaggerResponse {
  implicit val formats: DefaultFormats.type = DefaultFormats

  val UNAUTHORIZED = ResponseMessage(Elements.NOT_AUTHORIZED_CODE, "Authorization failed")
  val AUTHORIZED = ResponseMessage(Elements.OK_CODE, "KeyCloakToken")
}
