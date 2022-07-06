package com.ubirch.webui.models.keycloak

import java.security.{ Key, Security }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.keycloak.member.MemberType
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.tenant.Tenant
import com.ubirch.webui.models.keycloak.tenant.Tenant.GroupToTenant
import com.ubirch.webui.models.keycloak.util.Util
import com.ubirch.webui.services.connector.keycloak.PublicKeyGetter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jose4j.jwk.PublicJsonWebKey
import org.jose4j.jwt.consumer.{ JwtConsumerBuilder, JwtContext }
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.{ Failure, Success, Try }

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
    UserInfo(getRealm(token), getId(token), getUsername(token), getTenant(token))
  }

  private def buildKey(jwkJson: String): Key = {
    PublicJsonWebKey.Factory.newPublicJwk(jwkJson).getKey
  }

  def isUserDevice(accessToken: AccessToken): Boolean = {
    accessToken.getRealmAccess.getRoles.contains(Elements.DEVICE)
  }

  def getTenant(accessToken: AccessToken): Option[Tenant] = {
    if (isUserDevice(accessToken)) None else {
      val maybeTenantGroups = accessToken
        .getOtherClaims
        .getOrDefault("groups", new java.util.ArrayList[String]())
        .asInstanceOf[java.util.ArrayList[String]]
        .asScala
        .toList

      if (maybeTenantGroups.isEmpty) {
        logger.warn("No mapping configured for the groups/tenants in keycloak (JWT) for user: " + accessToken.getId)
        None
      } else {
        val tenantGroup = maybeTenantGroups
          .filter(_.startsWith(s"/$rootTenantName/$tenantNamePrefix"))
          .filter(!_.contains(organizationalUnitNamePrefix))

        if (tenantGroup.length > 1) throw new Exception(s"User(${accessToken.getId}) has more than one tenant group. Group paths: ${maybeTenantGroups.mkString(",")}")
        if (tenantGroup.isEmpty) logger.warn(s"User(${accessToken.getId}) doesn't have tenant group. This may cause some problems.")

        tenantGroup.headOption.map(tenantGroup => {
          val tenant = Util.getRealm(theRealmName).getGroupByPath(tenantGroup).groupRepresentationToTenant

          val subTenants = GroupFactory
            .getById(tenant.id)(theRealmName)
            .toRepresentation
            .getSubGroups
            .asScala
            .map(_.groupRepresentationToTenant)
            .toList

          tenant.copy(subTenants = subTenants)
        }) match {
          case Some(value) => Option(value)
          case None => GroupFactory.getDefaultTenant match {
            case Success(value) => Option(value)
            case Failure(_) => throw new Exception(s"Default tenant count not be found.")
          }
        }
      }
    }
  }
}
