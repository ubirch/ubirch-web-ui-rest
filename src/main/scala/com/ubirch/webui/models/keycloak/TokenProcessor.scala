package com.ubirch.webui.models.keycloak

import java.security.{ InvalidParameterException, Key, Security }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.keycloak.member.MemberType
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.models.Elements
import com.ubirch.webui.services.connector.keycloak.PublicKeyGetter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jose4j.base64url.Base64Url
import org.jose4j.jwk.PublicJsonWebKey
import org.jose4j.jws.EcdsaUsingShaAlgorithm
import org.jose4j.jwt.consumer.{ JwtConsumerBuilder, JwtContext }
import org.jose4j.jwx.CompactSerializer
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken

import scala.util.Try

object TokenProcessor extends ConfigBase with LazyLogging {

  private val JWK_HEADER_PART = 0
  private val JWK_BODY_PART = 1
  private val JWK_SIGNATURE_PART = 2

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

  /*
  Verify if signature of token is valid.
  KeyCloak produces invalid signature by default, this trick recreate the signature and makes it possible to verify that
  the token has been signed by KeyCloak (for ES256 token)
  cf https://bitbucket.org/b_c/jose4j/issues/134/token-created-by-keycloak-cannot-be and https://issues.jboss.org/browse/KEYCLOAK-9651

  21.10.2020: This comment and workaround becomes not necessary as with
  new version of keycloak, all works out well, without this workaround

   */
  def stopIfInvalidToken(tokenRaw: String): JwtContext = {
    val realm = theRealmName
    val keycloakPublicKey = Try(PublicKeyGetter.getKey(realm)).getOrElse({
      logger.error(s"Can not find public key of the realm: $realm")
      throw new Exception(s"Can not find public key of the realm: $realm")
    })

    //21.10.2020: This comment and workaround becomes not necessary as with
    //new version of keycloak, all works out well, without this workaround
    //val newToken: String = createCorrectTokenFromBadToken(tokenRaw)

    val jwtContext = new JwtConsumerBuilder()
      .setVerificationKey(buildKey(keycloakPublicKey))
      .setSkipDefaultAudienceValidation()
      .build()
      .process(tokenRaw)

    jwtContext
  }

  @deprecated("With Keycloak 11.02, this method is not required")
  private def createCorrectTokenFromBadToken(tokenRaw: String) = {
    val splitJwk = CompactSerializer.deserialize(tokenRaw)
    val signature = try {
      extractCorrectSignature(splitJwk)
    } catch {
      case _: Throwable => throw new Exception("Badly formatted JWT")
    }
    CompactSerializer.serialize(splitJwk(JWK_HEADER_PART), splitJwk(JWK_BODY_PART), Base64Url.encode(signature))

  }

  private def extractCorrectSignature(splitJwk: Array[String]): Array[Byte] = {

    (for {
      signature <- Try(splitJwk(JWK_SIGNATURE_PART))
      signatureBytesDer <- Try(Base64Url.decode(splitJwk(JWK_SIGNATURE_PART)))
        .recover { case e: Exception => throw new IllegalArgumentException("Error decoding", e) }
      a <- Try(EcdsaUsingShaAlgorithm.convertDerToConcatenated(signatureBytesDer, 64))
        .recover { case e: Exception => throw new IllegalArgumentException(s"Error @ convertDerToConcatenated sig=${signature}", e) }
    } yield {
      a
    }).recover {
      case e: Exception =>
        logger.error("error_extracting_sig -> ", e)
        throw e
    }.get

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
