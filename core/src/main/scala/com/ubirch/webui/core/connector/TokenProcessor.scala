package com.ubirch.webui.core.connector

import com.ubirch.webui.core.structure.UserInfo
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken

object TokenProcessor {

  def stringToToken(tokenAsString: String): AccessToken = {
    val r = TokenVerifier.create(tokenAsString, classOf[AccessToken])
    r.parse()
    r.getToken
  }

  def getRealm(token: AccessToken): String = {
    token.getIssuer.split("realms/")(1)
  }

  def getUsername(token: AccessToken): String = {
    token.getPreferredUsername
  }

  def getUserInfo(token: AccessToken): UserInfo = {
    UserInfo(getRealm(token), getUsername(token))
  }


}
