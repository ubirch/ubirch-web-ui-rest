package com.ubirch.webui.models.keycloak

import java.security.{ Key, Security }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.keycloak.member.MemberType
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.models.Elements
import com.ubirch.webui.services.connector.keycloak.PublicKeyGetter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jose4j.jwk.PublicJsonWebKey
import org.jose4j.jwt.consumer.{ JwtConsumerBuilder, JwtContext }
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken

import scala.util.Try

object TokenProcessor extends ConfigBase with LazyLogging {

  def validateToken(tokenRaw: String): Option[(UserInfo, MemberType)] = {
    Security.addProvider(new BouncyCastleProvider)
    stopIfInvalidToken(tokenRaw)
    val serializedKeyCloakAccessToken: AccessToken = toKeyCloakAccessToken(tokenRaw)

    if (isUserDevice(serializedKeyCloakAccessToken)) {
      Some((getUserInfo(serializedKeyCloakAccessToken), MemberType.Device))
    } else {
      Some((getUserInfo(serializedKeyCloakAccessToken), MemberType.User))
    }
  }

  def stopIfInvalidToken(tokenRaw: String): JwtContext = {
    val realm = theRealmName
    val keycloakPublicKey = Try(PublicKeyGetter.getKey(realm)).getOrElse({
      logger.error(s"Can not find public key of the realm: $realm")
      throw new Exception(s"Can not find public key of the realm: $realm")
    })

    val jwtContext = new JwtConsumerBuilder()
      .setVerificationKey(buildKey(keycloakPublicKey))
      .setSkipDefaultAudienceValidation()
      .build()
      .process(tokenRaw)

    jwtContext
  }

  def toKeyCloakAccessToken(tokenRaw: String): AccessToken = {
    val accessToken = TokenVerifier.create(tokenRaw, classOf[AccessToken])
    accessToken.getToken
  }

  def getRealm(token: AccessToken): String = {
    token.getIssuer.split("realms/")(1)
  }

  def getUsername(token: AccessToken): String = {
    token.getPreferredUsername
  }

  def getId(token: AccessToken): String = {
    token.getSubject
  }

  def getUserInfo(token: AccessToken): UserInfo = {
    UserInfo(getRealm(token), getId(token), getUsername(token))
  }

  private def buildKey(jwkJson: String): Key = {
    PublicJsonWebKey.Factory.newPublicJwk(jwkJson).getKey
  }

  def isUserDevice(accessToken: AccessToken): Boolean = {
    accessToken.getRealmAccess.getRoles.contains(Elements.DEVICE)
  }

}
