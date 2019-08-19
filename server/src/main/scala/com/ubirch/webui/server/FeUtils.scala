package com.ubirch.webui.server

import com.typesafe.scalalogging.LazyLogging
import javax.servlet.http.HttpServletRequest
import org.json4s.JsonAST.JNothing
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, Formats}

object FeUtils extends LazyLogging {

  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  def extractListOfSFromString(props: String): List[String] = {
    val jValue = parse(props)

    if (jValue == JNothing) {
      List.empty[String]
    } else {
      logger.info(jValue.extract[Map[String, String]].mkString(", "))
      jValue.extract[List[String]]
    }

  }

  def getToken(implicit request: HttpServletRequest): String = request.getHeader(tokenHeaderName)

  val tokenHeaderName = "Authorization"

}
