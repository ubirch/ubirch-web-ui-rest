package com.ubirch.webui.models.keycloak.util

import java.util
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.Hash
import com.ubirch.webui.models.Elements
import com.ubirch.webui.models.Exceptions.{ InternalApiException, MemberNotFound }
import com.ubirch.webui.models.keycloak.member.MemberType
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.services.connector.keycloak.KeyCloakConnector
import javax.ws.rs.core.Response.Status
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import org.keycloak.admin.client.resource.{ RealmResource, RoleResource, UserResource }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

object Util extends LazyLogging {

  def getDeviceGroupNameFromUserName(userName: String): String = Elements.PREFIX_OWN_DEVICES + userName
  def getApiConfigGroupName(realmName: String): String = realmName + Elements.PREFIX_API + "default"
  def getDeviceConfigGroupName(deviceType: String): String = Elements.PREFIX_DEVICE_TYPE + deviceType
  def getProviderGroupName(providerName: String): String = Elements.PROVIDER_GROUP_PREFIX + providerName
  def getUserFirstClaimedName(userName: String): String = Elements.FIRST_CLAIMED_GROUP_NAME_PREFIX + userName
  def getProviderClaimedDevicesName(providerName: String): String = Elements.CLAIMED + providerName

  def getRole(roleName: String)(implicit realmName: String): RoleResource = {
    Util.getRealm.roles().get(roleName)
  }

  def getCreatedId(response: Response): String = {
    val location = response.getLocation
    if (!(response.getStatusInfo == Status.CREATED)) {
      val statusInfo = response.getStatusInfo
      throw new WebApplicationException(
        "Create method returned status " + statusInfo.getReasonPhrase + " (Code: " + statusInfo.getStatusCode + "); expected status: Created (201)",
        response
      )
    }
    if (location == null) return null
    val path = location.getPath
    path.substring(path.lastIndexOf('/') + 1)
  }

  def stopIfHwdeviceidIsNotUUID(hwDeviceId: String): Unit = {
    if (!isStringUuid(hwDeviceId)) {
      throw new InternalApiException(s"hwDeviceId: $hwDeviceId is not a valid UUID")
    }
  }

  def stopIfMemberAlreadyExist(username: String)(implicit realmName: String): Unit = {
    try {
      val res = QuickActions.quickSearchUserNameGetAll(username)
      res.foreach { d =>
        if (d.getUsername.toLowerCase == username.toLowerCase) {
          logger.debug(s"member with username: $username already exists")
          throw new InternalApiException(s"$username already exists")
        }
      }
    } catch {
      case _: MemberNotFound =>
    }
  }

  def stopIfMemberAlreadyExistSecondaryIndex(secondaryIndex: String, nameOfSecondaryIndex: String = "IMSI")(implicit realmName: String): Unit = {
    try {
      QuickActions.quickSearchFirstNameStrict(secondaryIndex)
      logger.debug(s"user with $nameOfSecondaryIndex: $secondaryIndex already exists")
      throw new InternalApiException(s"member with $nameOfSecondaryIndex: $secondaryIndex already exists")
    } catch {
      case _: MemberNotFound =>
    }
  }

  def singleTypeToStupidJavaList[T](toConvert: T): util.List[T] = {
    val stupidJavaList = new util.ArrayList[T]()
    stupidJavaList.add(toConvert)
    stupidJavaList
  }

  def getCustomerId(realmName: String): String = {
    com.ubirch.crypto.utils.Utils.hashToHex(realmName, Hash.SHA256)
  }

  /*
  Check if a member is of a certain type (USER or DEVICE)
   */
  def memberType(userId: String)(implicit realmName: String): MemberType = {
    val member = Option(Util.getRealm.users().get(userId)) match {
      case Some(user) => user
      case None =>
        throw MemberNotFound(
          s"no member in realm $realmName with id $userId was found"
        )
    }
    if (isMemberDevice(member)) {
      MemberType.Device
    } else MemberType.User
  }

  def getRealm(implicit realmName: String): RealmResource = {
    KeyCloakConnector.get.connector.realm(realmName)
  }

  def isMemberDevice(memberKC: UserResource): Boolean =
    memberKC.roles().realmLevel().listEffective().asScala.toList.exists { m =>
      m.getName.equalsIgnoreCase(Elements.DEVICE)
    }

  def getCurrentTimeIsoString: String = {
    import java.text.SimpleDateFormat
    import java.util.TimeZone
    val timeZone = TimeZone.getTimeZone("UTC")
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") // Quoted "Z" to indicate UTC, no timezone offset
    dateFormat.setTimeZone(timeZone)
    dateFormat.format(new Date())
  }

  /**
    * Check if the string is a uuid
    * @param str string to check
    * @return true if it can be casted to a uuid, false otherwise
    */
  def isStringUuid(str: String): Boolean = {
    import java.util.UUID
    Try(UUID.fromString(str)) match {
      case Failure(_) => false
      case Success(_) => true
    }
  }

  /**
    * Will convert a raw device attribute to a nicely formated scala map
    * @param attributes The raw attributes obtained by representation.getAttributes
    */
  def attributesToMap(attributes: java.util.Map[String, java.util.List[String]]): Map[String, List[String]] = {
    if (attributes == null || attributes.isEmpty) Map.empty else {
      attributes.asScala.toMap map { keyValue =>
        keyValue._1 -> keyValue._2.asScala.toList
      }
    }
  }
}
