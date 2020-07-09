package com.ubirch.webui.services.connector.keycloak

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.native.JsonMethods._

import scala.collection.mutable

object PublicKeyGetter extends ConfigBase with LazyLogging {

  var keys: mutable.Map[String, String] = mutable.Map.empty

  def getKey(realmName: String = "ubirch-default-realm"): String = {

    keys.get(realmName) match {
      case Some(k) => k
      case None =>
        val key = getSpecificKeyFromServer(realmName)
        keys += (realmName -> key)
        key
    }
  }

  def getSpecificKeyFromServer(realmName: String): String = {
    implicit val formats: DefaultFormats.type = DefaultFormats

    val baseUrl = (parse(keyCloakJson) \ "auth-server-url").extract[String]
    val getRequestRes = scala.io.Source.fromURL(s"$baseUrl/realms/$realmName/protocol/openid-connect/certs").mkString
    logger.debug("all keys received: " + getRequestRes)

    val json = parse(getRequestRes)
    val allKeysInRealm = (json \ "keys").extract[List[JValue]]
    val ES256Key = allKeysInRealm.filter(json => (json \ "alg").extract[String].equals("ES256"))
    val ES256KeyString = compact(render(ES256Key)).tail.dropRight(1)
    logger.debug("key = " + ES256KeyString)
    ES256KeyString

  }

}
