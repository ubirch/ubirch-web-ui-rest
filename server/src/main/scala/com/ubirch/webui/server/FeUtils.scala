package com.ubirch.webui.server

import com.typesafe.scalalogging.LazyLogging
import javax.servlet.http.HttpServletRequest
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

object FeUtils extends LazyLogging {

  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  def getToken(implicit request: HttpServletRequest): String = request.getHeader(tokenHeaderName)

  val tokenHeaderName = "Authorization"

  def createServerError(errorType: String, message: String): String = {
    val errorMessage = "error" ->
      ("error type" -> errorType) ~
      ("message" -> message)
    pretty(render(errorMessage))
  }
}
