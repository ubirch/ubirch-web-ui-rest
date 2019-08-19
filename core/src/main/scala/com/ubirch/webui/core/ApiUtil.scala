package com.ubirch.webui.core

import java.util

import com.typesafe.scalalogging.LazyLogging
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import org.keycloak.admin.client.resource._
import org.keycloak.crypto.Algorithm
import org.keycloak.representations.idm.CredentialRepresentation.PASSWORD
import org.keycloak.representations.idm._

import scala.collection.JavaConverters._

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

  def findClientResourceById(realm: RealmResource, id: String): ClientResource = {
    for (c <- realm.clients.findAll.asScala) {
      if (c.getId == id) return realm.clients.get(c.getId)
    }
    null
  }

  def findClientResourceByClientId(realm: RealmResource, clientId: String): ClientResource = {
    for (c <- realm.clients.findAll.asScala) {
      if (c.getClientId == clientId) return realm.clients.get(c.getId)
    }
    null
  }

  def findClientResourceByName(realm: RealmResource, name: String): ClientResource = {
    for (c <- realm.clients.findAll.asScala) {
      if (name == c.getName) return realm.clients.get(c.getId)
    }
    null
  }

  def findClientByClientId(realm: RealmResource, clientId: String): ClientResource = {
    for (c <- realm.clients.findAll.asScala) {
      if (clientId == c.getClientId) return realm.clients.get(c.getId)
    }
    null
  }

  def findClientRoleByName(client: ClientResource, role: String): RoleResource = client.roles.get(role)

  def findProtocolMapperByName(client: ClientResource, name: String): ProtocolMapperRepresentation = {
    for (p <- client.getProtocolMappers.getMappers.asScala) {
      if (p.getName == name) return p
    }
    null
  }

  def findClientScopeByName(realm: RealmResource, clientScopeName: String): ClientScopeResource = {
    for (clientScope <- realm.clientScopes.findAll.asScala) {
      if (clientScopeName == clientScope.getName) return realm.clientScopes.get(clientScope.getId)
    }
    null
  }

  def findRealmRoleByName(realm: RealmResource, role: String): RoleResource = realm.roles.get(role)

  def findUserByUsername(realm: RealmResource, username: String): UserRepresentation = {
    var user: UserRepresentation = null
    val ur: util.List[UserRepresentation] = realm.users.search(username, null, null, null, 0, Integer.MAX_VALUE)
    if (ur.size == 1) user = ur.get(0)
    if (ur.size > 1) { // try to be more specific
      for (rep <- ur.asScala) {
        if (rep.getUsername.equalsIgnoreCase(username)) return rep
      }
    }
    user
  }

  def findUserByUsernameId(realm: RealmResource, username: String): UserResource = realm.users.get(findUserByUsername(realm, username).getId)

  /**
    * Creates a user
    * @return ID of the new user
    */
  def createUserWithAdminClient(realm: RealmResource, user: UserRepresentation): String = {
    val response = realm.users.create(user)
    val createdId = getCreatedId(response)
    response.close()
    createdId
  }

  /**
    * Creates a user and sets the password
    * @return ID of the new user
    */
  def createUserAndResetPasswordWithAdminClient(realm: RealmResource, user: UserRepresentation, password: String): String = {
    val id = createUserWithAdminClient(realm, user)
    resetUserPassword(realm.users.get(id), password, temporary = false)
    id
  }

  def resetUserPassword(userResource: UserResource, newPassword: String, temporary: Boolean): Unit = {
    val newCredential = new CredentialRepresentation
    newCredential.setType(PASSWORD)
    newCredential.setValue(newPassword)
    newCredential.setTemporary(temporary)
    userResource.resetPassword(newCredential)
  }

  def assignRealmRoles(realm: RealmResource, userId: String, roles: String*): Unit = {
    val realmName = realm.toRepresentation.getRealm
    val roleRepresentations = new util.ArrayList[RoleRepresentation]
    for (roleName <- roles) {
      val role = realm.roles.get(roleName).toRepresentation
      roleRepresentations.add(role)
    }
    val userResource = realm.users.get(userId)
    logger.info("assigning roles " + roles.mkString(", ") + " to user: \"" + userResource.toRepresentation.getUsername + "\" in realm: \"" + realmName + "\"")
    userResource.roles.realmLevel.add(roleRepresentations)
  }

  def removeUserByUsername(realmResource: RealmResource, username: String): Unit = {
    val user = findUserByUsername(realmResource, username)
    if (user != null) realmResource.users.delete(user.getId)
  }

  def assignClientRoles(realm: RealmResource, userId: String, clientName: String, roles: String*): Unit = {
    val realmName = realm.toRepresentation.getRealm
    var clientId = ""
    for (clientRepresentation <- realm.clients.findAll.asScala) {
      if (clientRepresentation.getClientId == clientName) clientId = clientRepresentation.getId
    }
    if (!clientId.isEmpty) {
      val clientResource = realm.clients.get(clientId)
      val roleRepresentations = new util.ArrayList[RoleRepresentation]
      for (roleName <- roles) {
        val role = clientResource.roles.get(roleName).toRepresentation
        roleRepresentations.add(role)
      }
      val userResource = realm.users.get(userId)
      logger.info("assigning role: " + roles.mkString(", ") + " to user: \"" + userResource.toRepresentation.getUsername + "\" of client: \"" + clientName + "\" in realm: \"" + realmName + "\"")
      userResource.roles.clientLevel(clientId).add(roleRepresentations)
    } else logger.warn("client with name " + clientName + " doesn't exist in realm " + realmName)
  }

  def groupContainsSubgroup(group: GroupRepresentation, subgroup: GroupRepresentation): Boolean = {
    var contains = false
    for (sg <- group.getSubGroups.asScala) {
      if (!contains) {
        if (subgroup.getId == sg.getId) {
          contains = true
        }
      }
    }
    contains
  }

  def findAuthorizationSettings(realm: RealmResource, clientId: String): AuthorizationResource = {
    for (c <- realm.clients.findAll.asScala) {
      if (c.getClientId == clientId) return realm.clients.get(c.getId).authorization
    }
    null
  }

  def findActiveKey(realm: RealmResource): KeysMetadataRepresentation.KeyMetadataRepresentation = {
    val keyMetadata = realm.keys.getKeyMetadata
    val activeKid = keyMetadata.getActive.get(Algorithm.RS256)
    for (rep <- keyMetadata.getKeys.asScala) {
      if (rep.getKid == activeKid) return rep
    }
    null
  }
}

