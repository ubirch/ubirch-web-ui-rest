package com.ubirch.webui.core.connector

import java.security.{Key, Security}

import com.ubirch.webui.core.structure.UserInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jose4j.base64url.Base64Url
import org.jose4j.jwk.PublicJsonWebKey
import org.jose4j.jws.EcdsaUsingShaAlgorithm
import org.jose4j.jwt.consumer.{JwtConsumerBuilder, JwtContext}
import org.jose4j.jwx.CompactSerializer
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken

object TokenProcessor {

  def validateToken(tokenRaw: String): UserInfo = {
    Security.addProvider(new BouncyCastleProvider)
    verifySignatureAndParseToken(tokenRaw)
    val token = stringToToken(tokenRaw)
    getUserInfo(token)
  }

  /*
  Verify if signature of token is valid.
  KeyCloak produces invalid signature by default, this trick recreate the signature and makes it possible to verify that
  the token has been signed by KeyCloak
  cf https://bitbucket.org/b_c/jose4j/issues/134/token-created-by-keycloak-cannot-be and https://issues.jboss.org/browse/KEYCLOAK-9651
   */
  def verifySignatureAndParseToken(tokenRaw: String): JwtContext = {
    // TODO: put jwt elsewhere
    val jwt =
      """{"kid":"0AaUvzQhEljiSfwM1EHOFiY-ot7gpMkE6Ae5wvAtWRQ","kty":"EC","alg":"ES256","use":"sig","crv":"P-256","x":"WviSnOUu2h_iHsIkmAOlPW9Ej7qIIeRz-QxK_XhSJIk","y":"0dRaPC2pZ7PECGQItGqEVwzoxc-tGnBTLf3NSU-IM48"}"""
    val parts = CompactSerializer.deserialize(tokenRaw)
    val signatureBytesDer = Base64Url.decode(parts(2))
    val signatureBytesConcat = EcdsaUsingShaAlgorithm.convertDerToConcatenated(signatureBytesDer, 64)
    val newToken = CompactSerializer.serialize(parts(0), parts(1), Base64Url.encode(signatureBytesConcat))

    new JwtConsumerBuilder().setVerificationKey(buildKey(jwt)).setSkipDefaultAudienceValidation().build.process(newToken)

  }

  def stringToToken(tokenRaw: String): AccessToken = {
    val r = TokenVerifier.create(tokenRaw, classOf[AccessToken])
    r.getToken
  }

  def getRealm(token: AccessToken): String = {
    token.getIssuer.split("realms/")(1)
  }

  def getUsername(token: AccessToken): String = {
    token.getPreferredUsername
  }

  def getId(token: AccessToken): String = {
    token.getId
  }

  def getUserInfo(token: AccessToken): UserInfo = {
    UserInfo(getRealm(token), getId(token), getUsername(token))
  }

  private def buildKey(jwkJson: String): Key = {
    PublicJsonWebKey.Factory.newPublicJwk(jwkJson).getKey
  }




}
