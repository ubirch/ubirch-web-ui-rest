package com.ubirch.webui.core

import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import org.keycloak.admin.client.resource._
import org.keycloak.representations.idm.CredentialRepresentation.PASSWORD
import org.keycloak.representations.idm._

object ApiUtil extends LazyLogging {

  def getCreatedId(response: Response): String = {
    val location = response.getLocation
    if (!(response.getStatusInfo == Status.CREATED)) {
      val statusInfo = response.getStatusInfo
      throw new WebApplicationException("Create method returned status " + statusInfo.getReasonPhrase + " (Code: " + statusInfo.getStatusCode + "); expected status: Created (201)", response)
    }
    if (location == null) return null
    val path = location.getPath
    path.substring(path.lastIndexOf('/') + 1)
  }

  def resetUserPassword(userResource: UserResource, newPassword: String, temporary: Boolean): Unit = {
    val newCredential = new CredentialRepresentation
    newCredential.setType(PASSWORD)
    newCredential.setValue(newPassword)
    newCredential.setTemporary(temporary)
    userResource.resetPassword(newCredential)
  }

}

