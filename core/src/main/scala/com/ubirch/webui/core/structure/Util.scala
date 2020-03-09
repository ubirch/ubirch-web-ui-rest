package com.ubirch.webui.core.structure

import java.io.{File, PrintWriter}
import java.util
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.Hash
import com.ubirch.webui.core.Exceptions.{InternalApiException, MemberNotFound}
import com.ubirch.webui.core.connector.keycloak.KeyCloakConnector
import com.ubirch.webui.core.structure.member.{MemberFactory, MemberType}
import com.ubirch.webui.core.structure.member.MemberType.MemberType
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import org.keycloak.admin.client.resource.{RealmResource, RoleResource, UserResource}

import scala.collection.JavaConverters._

object Util extends LazyLogging {

  def getDeviceGroupNameFromUserName(userName: String): String =
    Elements.PREFIX_OWN_DEVICES + userName
  def getApiConfigGroupName(realmName: String): String =
    realmName + Elements.PREFIX_API + "default"
  def getDeviceConfigGroupName(deviceType: String): String =
    Elements.PREFIX_DEVICE_TYPE + deviceType
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

  def stopIfMemberAlreadyExist(username: String)(implicit realmName: String): Unit = {
    try {
      MemberFactory.getByUsername(username, MemberType.Device)
      MemberFactory.getByUsername(username, MemberType.User)
      throw new InternalApiException(
        s"member with username: $username already exists"
      )
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

  def createTempFile(contents: String, prefix: Option[String] = None, suffix: Option[String] = None): File = {
    val tempFi = File.createTempFile(
      prefix.getOrElse("prefix-"),
      suffix.getOrElse("-suffix")
    )
    tempFi.deleteOnExit()
    new PrintWriter(tempFi) {
      try {
        write(contents)
      } finally {
        close()
      }
    }
    logger.debug("temp file created at: " + tempFi.getAbsolutePath)
    tempFi
  }

  def getCurrentTimeIsoString() = {
    import java.text.SimpleDateFormat
    import java.util.TimeZone
    val timeZone = TimeZone.getTimeZone("UTC")
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") // Quoted "Z" to indicate UTC, no timezone offset
    dateFormat.setTimeZone(timeZone)
    dateFormat.format(new Date())
  }

}
