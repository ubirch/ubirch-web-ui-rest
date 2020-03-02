package com.ubirch.webui.core

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.structure.{AddDevice, Elements, SimpleUser, Util}
import com.ubirch.webui.core.structure.group.{Group, GroupFactory}
import com.ubirch.webui.core.structure.member.{Device, User}
import com.ubirch.webui.test.Elements
import javax.ws.rs.core.Response
import org.keycloak.admin.client.resource.{RealmResource, RoleResource, UserResource}
import org.keycloak.representations.idm.{GroupRepresentation, RoleRepresentation, UserRepresentation}
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.util.Random

object TestRefUtil extends LazyLogging with Matchers with Elements {

  implicit val realmName: String = "test-realm"

  def clearKCRealm(implicit realm: RealmResource): Unit = {
    // removing users
    val users = realm.users().list().asScala.toList
    if (users.nonEmpty) {
      users foreach { u =>
        realm.users().get(u.getId).remove()
      }
    }

    // removing groups
    val groups = realm.groups().groups().asScala.toList
    if (groups.nonEmpty) {
      groups.foreach { g =>
        realm.groups().group(g.getId).remove()
      }
    }

    // removing realm roles
    val roles = realm.roles().list().asScala.toList
    if (roles.nonEmpty) {
      roles.foreach { r =>
        realm.roles().deleteRole(r.getName)
      }
    }
  }

  def createAndGetSimpleRole(
      roleName: String
  )(implicit realm: RealmResource): RoleResource = {
    val roleRepresentation = new RoleRepresentation()
    roleRepresentation.setName(roleName)
    realm.roles().create(roleRepresentation)
    realm.roles().get(roleName)
  }

  def addUserToKC(user: SimpleUser)(implicit realm: RealmResource): User = {
    addUserToKC(user.username, user.firstname, user.lastname)
  }

  def addUserToKC(userName: String, firstName: String, lastName: String)(
      implicit
      realm: RealmResource
  ): User = {
    val userRepresentation = new UserRepresentation
    userRepresentation.setUsername(userName)
    userRepresentation.setFirstName(firstName)
    userRepresentation.setLastName(lastName)
    val res = realm.users().create(userRepresentation)
    val idUser = ApiUtil.getCreatedId(res)
    new User(realm.users().get(idUser))
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

  def createGroupWithConf(
      attributes: util.Map[String, util.List[String]],
      groupName: String
  )(implicit realm: RealmResource): Group = {
    val groupConf = new GroupRepresentation
    groupConf.setAttributes(attributes)
    val groupKC = createSimpleGroup(groupName)
    groupKC.keyCloakGroup.update(groupConf)
    groupKC.getUpdatedGroup
  }

  def createSimpleGroup(groupName: String)(implicit realm: RealmResource): Group = {
    val groupRep = new GroupRepresentation
    groupRep.setName(groupName)
    val res = realm.groups().add(groupRep)
    val idGroup = ApiUtil.getCreatedId(res)
    new Group(realm.groups().group(idGroup))
  }

  def generateDeviceAttributes(dType: String = "default_type", hwDeviceId: String = "", description: String = ""): (String, String, String) = {
    val realHwDeviceId =
      if (hwDeviceId != "") hwDeviceId else giveMeRandomString()
    val realDescription = if (description != "") description else realHwDeviceId
    (realHwDeviceId, dType, realDescription)
  }

  def verifyDeviceWasCorrectlyAdded(
      deviceRoleName: String,
      hwDeviceId: String,
      apiConfigGroup: Group,
      deviceConfigGroup: Group,
      userGroupName: String,
      listGroupsId: List[String],
      description: String
  )(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = deviceKc.toRepresentation.getAttributes.asScala.toMap
    val apiAttributes = apiConfigGroup.getAttributes
    val deviceConfAttributes = deviceConfigGroup.getAttributes
    // check attributes
    deviceAttributes shouldBe (apiAttributes.attributes ++ deviceConfAttributes.attributes)
    // check group membership
    val deviceGroups = deviceKc.groups().asScala.toList
    val deviceGroupsId = deviceGroups map { x =>
      x.getId
    }
    val userGroupId = realm.groups().groups(userGroupName, 0, 1).get(0).getId
    val lGroupsId = apiConfigGroup.id :: deviceConfigGroup.id :: userGroupId :: listGroupsId
    deviceGroupsId.sortBy(x => x) shouldBe lGroupsId.sortBy(x => x)

    // check roles
    val deviceRole =
      deviceKc.roles().realmLevel().listEffective().asScala.toList
    deviceRole.exists(x => x.getName == deviceRoleName) shouldBe true

    // check normal infos
    deviceKc.toRepresentation.getLastName shouldBe description
    deviceKc.toRepresentation.getUsername shouldBe hwDeviceId.toLowerCase
  }

  def verifyDeviceWasCorrectlyAddedAdmin(deviceRoleName: String, hwDeviceId: String, apiConfigGroup: Group,
                                         deviceConfigGroup: Group, userGroupName: String, listGroupsId: List[String],
                                         description: String, provider: String, secondaryIndex: String = Elements.DEFAULT_FIRST_NAME)(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = deviceKc.toRepresentation.getAttributes.asScala.toMap
    val apiAttributes = apiConfigGroup.getAttributes
    val deviceConfAttributes = deviceConfigGroup.getAttributes
    val providerGroup = GroupFactory.getByName(Util.getProviderGroupName(provider))
    val unclaimedDevicesGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
    // check attributes
    deviceAttributes shouldBe (apiAttributes.attributes ++ deviceConfAttributes.attributes)
    // check group membership
    val deviceGroups = deviceKc.groups().asScala.toList
    val deviceGroupsId = deviceGroups map { x =>
      x.getId
    }
    val lGroupsId = apiConfigGroup.id :: deviceConfigGroup.id :: providerGroup.id :: unclaimedDevicesGroup.id :: listGroupsId
    deviceGroupsId.sortBy(x => x) shouldBe lGroupsId.sortBy(x => x)

    // check roles
    val deviceRole =
      deviceKc.roles().realmLevel().listEffective().asScala.toList
    deviceRole.exists(x => x.getName == deviceRoleName) shouldBe true

    // check normal infos
    deviceKc.toRepresentation.getLastName shouldBe description
    deviceKc.toRepresentation.getUsername shouldBe hwDeviceId.toLowerCase
    deviceKc.toRepresentation.getFirstName shouldBe secondaryIndex
  }

  def createSimpleUser()(implicit realmName: String): User = {
    def realm = Util.getRealm

    val username = giveMeRandomString()
    val lastName = giveMeRandomString()
    val firstName = giveMeRandomString()
    val userRep = new UserRepresentation
    userRep.setUsername(username)
    userRep.setLastName(lastName)
    userRep.setFirstName(firstName)
    val res = realm.users().create(userRep)
    new User(realm.users().get(ApiUtil.getCreatedId(res)))
  }

  def giveMeRandomString(size: Int = 32): String = {
    Random.alphanumeric.take(size).mkString
  }

  def createRandomDevice()(implicit realm: RealmResource): Device = {
    // vals
    val userStruct = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)

    val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = DEFAULT_DESCRIPTION)

    val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)
    println(createGroupsName(userStruct.username, realmName, deviceType)._2)

    val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

    val deviceConfigRepresentation = new GroupRepresentation
    deviceConfigRepresentation.setAttributes(attributeDConf)

    // create groups
    val (userGroup, apiConfigGroup, _) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)
    println(apiConfigGroup.name)
    // create user
    val user = TestRefUtil.addUserToKC(userStruct)
    // make user join groups
    user.joinGroup(userGroup)
    user.joinGroup(apiConfigGroup)

    val listGroupsToJoinId = List()
    // create roles
    TestRefUtil.createAndGetSimpleRole(Elements.DEVICE)
    val userRole = TestRefUtil.createAndGetSimpleRole(Elements.USER)
    user.addRole(userRole.toRepresentation)
    // create device and return device id
    user.createNewDevice(
      AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId)
    )
  }

  def createGroupsName(username: String, realmName: String, deviceType: String): (String, String, String) = {
    val userGroupName = Util.getDeviceGroupNameFromUserName(username)
    val apiConfigName = Util.getApiConfigGroupName(realmName)
    val deviceConfName = Util.getDeviceConfigGroupName(deviceType)
    (userGroupName, apiConfigName, deviceConfName)
  }

  def createGroups(userGroupName: String)(attributeApi: util.Map[String, util.List[String]], apiConfName: String)(attributeDevice: util.Map[String, util.List[String]], deviceConfName: String)(implicit realm: RealmResource): (Group, Group, Group) = {
    val userGroup = TestRefUtil.createSimpleGroup(userGroupName)
    val apiConfigGroup = TestRefUtil.createGroupWithConf(attributeApi, apiConfName)
    val deviceConfigGroup = TestRefUtil.createGroupWithConf(attributeDevice, deviceConfName)
    (userGroup, apiConfigGroup, deviceConfigGroup)
  }
}
