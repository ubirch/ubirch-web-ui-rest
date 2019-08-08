package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.operations.Utils._
import com.ubirch.webui.core.structure.User
import javax.ws.rs.core.Response
import org.keycloak.admin.client.resource.{GroupResource, RealmResource, RoleResource, UserResource}
import org.keycloak.representations.idm.{GroupRepresentation, RoleRepresentation, UserRepresentation}
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.util.Random

object TestUtils extends LazyLogging with Matchers {

  def clearKCRealm(implicit realm: RealmResource): Unit = {
    // removing users
    val users = realm.users().list().asScala.toList
    if (users.nonEmpty) {
      users foreach { u => realm.users().get(u.getId).remove() }
    }

    // removing groups
    val groups = realm.groups().groups().asScala.toList
    if (groups.nonEmpty) {
      groups.foreach { g => realm.groups().group(g.getId).remove() }
    }

    // removing realm roles
    val roles = realm.roles().list().asScala.toList
    if (roles.nonEmpty) {
      roles.foreach { r => realm.roles().deleteRole(r.getName) }
    }
  }

  def createAndGetSimpleRole(roleName: String)(implicit realm: RealmResource): RoleResource = {
    val roleRepresentation = new RoleRepresentation()
    roleRepresentation.setName(roleName)
    realm.roles().create(roleRepresentation)
    realm.roles().get(roleName)
  }

  def addUserToKC(userName: String, firstName: String, lastName: String)(implicit realm: RealmResource): UserResource = {
    val userRepresentation = new UserRepresentation
    userRepresentation.setUsername(userName)
    userRepresentation.setFirstName(firstName)
    userRepresentation.setLastName(lastName)
    val res = realm.users().create(userRepresentation)
    val idUser = ApiUtil.getCreatedId(res)
    realm.users().get(idUser)
  }

  def addUserToKC(user: User)(implicit realm: RealmResource): UserResource = {
    addUserToKC(user.username, user.firstname, user.lastname)
  }

  def createSimpleGroup(groupName: String)(implicit realm: RealmResource): GroupResource = {
    val groupRep = new GroupRepresentation
    groupRep.setName(groupName)
    val res = realm.groups().add(groupRep)
    val idGroup = ApiUtil.getCreatedId(res)
    realm.groups().group(idGroup)
  }

  def deleteUser(userId: String)(implicit realm: RealmResource): Response = {
    realm.users().delete(userId)
  }

  def deleteGroup(groupId: String)(implicit realm: RealmResource): Unit = {
    realm.groups().group(groupId).remove()
  }

  def addRoleToUser(user: UserResource, role: RoleRepresentation): Unit = {
    val roleRepresentationList = new util.ArrayList[RoleRepresentation](1)
    roleRepresentationList.add(role)
    user.roles().realmLevel().add(roleRepresentationList)
  }

  def createGroupWithConf(attributes: util.Map[String, util.List[String]], groupName: String)(implicit realm: RealmResource): GroupResource = {
    val groupConf = new GroupRepresentation
    groupConf.setAttributes(attributes)
    val groupKC = createSimpleGroup(groupName)
    groupKC.update(groupConf)
    groupKC
  }

  def generateDeviceAttributes(dType: String = "default_type", hwDeviceId: String = "", description: String = ""): (String, String, String) = {
    val realHwDeviceId = if (hwDeviceId != "") hwDeviceId else giveMeRandomString()
    val realDescription = if (description != "") description else realHwDeviceId
    (realHwDeviceId, dType, realDescription)
  }

  def giveMeRandomString(size: Int = 32): String = {
    Random.alphanumeric.take(size).mkString
  }

  def verifyDeviceWasCorrectlyAdded(deviceRoleName: String, hwDeviceId: String, apiConfigGroup: GroupResource, deviceConfigGroup: GroupResource,
                                    userGroupName: String, listGroupsId: List[String], description: String)(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = deviceKc.toRepresentation.getAttributes.asScala.toList
    val apiAttributes = apiConfigGroup.toRepresentation.getAttributes.asScala.toList
    val deviceConfAttributes = deviceConfigGroup.toRepresentation.getAttributes.asScala.toList
    // check attributes
    deviceAttributes.sortBy(x => x._1) shouldBe (apiAttributes ++ deviceConfAttributes).sortBy(x => x._1)
    // check group membership
    val deviceGroups = deviceKc.groups().asScala.toList
    val deviceGroupsId = deviceGroups map { x => x.getId }
    val userGroupId = realm.groups().groups(userGroupName, 0, 1).get(0).getId
    val lGroupsId = apiConfigGroup.toRepresentation.getId :: deviceConfigGroup.toRepresentation.getId :: userGroupId :: listGroupsId
    deviceGroupsId.sortBy(x => x) shouldBe lGroupsId.sortBy(x => x)

    // check roles
    val deviceRole = deviceKc.roles().realmLevel().listEffective().asScala.toList
    deviceRole.exists(x => x.getName == deviceRoleName) shouldBe true

    // check normal infos
    deviceKc.toRepresentation.getLastName shouldBe description
    deviceKc.toRepresentation.getUsername shouldBe hwDeviceId.toLowerCase
  }

  def createSimpleUser()(implicit realmName: String): UserResource = {
    def realm = getRealm

    val username = giveMeRandomString()
    val lastName = giveMeRandomString()
    val firstName = giveMeRandomString()
    val userRep = new UserRepresentation
    userRep.setUsername(username)
    userRep.setLastName(lastName)
    userRep.setFirstName(firstName)
    val res = realm.users().create(userRep)
    realm.users().get(ApiUtil.getCreatedId(res))
  }
}
