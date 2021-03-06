package com.ubirch.webui

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.{ AddDevice, DeviceStub, SimpleUser }
import com.ubirch.webui.models.keycloak.group.GroupFactory
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.member.DeviceFactory
import com.ubirch.webui.TestRefUtil.createGroupWithConf
import com.ubirch.webui.models.keycloak.util.{ GroupResourceRepresentation, MemberResourceRepresentation, Util }
import javax.ws.rs.core.Response
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JString
import org.keycloak.admin.client.resource.{ RealmResource, RoleResource, UserResource }
import org.keycloak.representations.idm.{ CredentialRepresentation, GroupRepresentation, RoleRepresentation, UserRepresentation }
import org.scalatest.Matchers
import org.json4s.jackson.JsonMethods.parse

import scala.collection.JavaConverters._
import scala.util.Random

object TestRefUtil extends LazyLogging with Matchers with Elements {

  implicit val realmName: String = DEFAULT_REALM_NAME

  val defaultUser: SimpleUser = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)
  val defaultDevice: DeviceStub = DeviceStub(giveMeRandomUUID, description = DEFAULT_DESCRIPTION, "default_type", true)
  val defaultUserDevice = UserDevices(defaultUser, maybeDevicesShould = Option(List(defaultDevice)))
  val defaultUsers = Option(UsersDevices(List(defaultUserDevice)))
  val defaultApiConfGroup = GroupWithAttribute(Util.getApiConfigGroupName(realmName), DEFAULT_MAP_ATTRIBUTE_API_CONF)
  val defaultDeviceGroup = GroupWithAttribute(Util.getDeviceConfigGroupName(DEFAULT_TYPE), DEFAULT_MAP_ATTRIBUTE_D_CONF)
  val defaultConfGroups = Option(GroupsWithAttribute(List(defaultApiConfGroup, defaultDeviceGroup)))
  def defaultInitKeycloakBuilder = InitKeycloakBuilder(users = defaultUsers, defaultGroups = defaultConfGroups)

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

  def createAndGetSimpleRole(roleName: String)(implicit realm: RealmResource): RoleResource = {
    val roleRepresentation = new RoleRepresentation()
    roleRepresentation.setName(roleName)
    realm.roles().create(roleRepresentation)
    realm.roles().get(roleName)
  }

  def getRole(roleName: String)(implicit realm: RealmResource): RoleResource = realm.roles().get(roleName)

  def addUserToKC(user: SimpleUser, maybeAttr: Option[java.util.Map[String, java.util.List[String]]] = None)(implicit realm: RealmResource): MemberResourceRepresentation = {
    addUserToKCExplodedView(user.username, user.firstname, user.lastname, maybeAttr)
  }

  def addUserToKCExplodedView(userName: String, firstName: String, lastName: String, maybeAttr: Option[java.util.Map[String, java.util.List[String]]] = None)(implicit realm: RealmResource): MemberResourceRepresentation = {
    val userRepresentation = new UserRepresentation
    userRepresentation.setUsername(userName)
    userRepresentation.setFirstName(firstName)
    userRepresentation.setLastName(lastName)
    maybeAttr match {
      case Some(attr) =>
        userRepresentation.setAttributes(attr)
      case None =>
    }
    userRepresentation.setEnabled(true)

    val userCredential = new CredentialRepresentation
    userCredential.setValue("password")
    userCredential.setTemporary(false)
    userCredential.setType(CredentialRepresentation.PASSWORD)

    userRepresentation.setCredentials(Util.singleTypeToStupidJavaList[CredentialRepresentation](userCredential))

    val res = realm.users().create(userRepresentation)
    val idUser = ApiUtil.getCreatedId(res)
    realm.users().get(idUser).toResourceRepresentation
  }

  def deleteUser(userId: String)(implicit realm: RealmResource): Response = {
    realm.users().delete(userId)
  }

  def deleteGroup(groupId: String)(implicit realm: RealmResource): Unit = {
    realm.groups().group(groupId).remove()
  }

  def addRoleToUser(user: UserResource, role: RoleRepresentation): Unit = {
    val roleRepresentationList = new java.util.ArrayList[RoleRepresentation](1)
    roleRepresentationList.add(role)
    user.roles().realmLevel().add(roleRepresentationList)
  }

  def createGroupWithConf(attributes: java.util.Map[String, java.util.List[String]], groupName: String)(implicit realm: RealmResource) = {
    val groupConf = new GroupRepresentation
    groupConf.setAttributes(attributes)
    val groupKC = createSimpleGroup(groupName)
    groupKC.resource.update(groupConf)
    groupKC.getUpdatedGroup
  }

  def createSimpleGroup(groupName: String)(implicit realm: RealmResource): GroupResourceRepresentation = {
    val groupRep = new GroupRepresentation
    groupRep.setName(groupName)
    val res = realm.groups().add(groupRep)
    val idGroup = ApiUtil.getCreatedId(res)
    realm.groups().group(idGroup).toResourceRepresentation
  }

  def generateDeviceAttributes(dType: String = "default_type", hwDeviceId: String = "", description: String = ""): (String, String, String) = {
    val realHwDeviceId = if (hwDeviceId != "") hwDeviceId else giveMeRandomUUID
    val realDescription = if (description != "") description else realHwDeviceId
    (realHwDeviceId, dType, realDescription)
  }

  def generateDeviceAttributesWithSecIndex(dType: String = "default_type", hwDeviceId: String = "", description: String = "", secondaryIndex: String = ""): (String, String, String, String) = {
    val realHwDeviceId =
      if (hwDeviceId != "") hwDeviceId else giveMeRandomUUID
    val realDescription = if (description != "") description else realHwDeviceId
    val secIndex = if (secondaryIndex != "") secondaryIndex else giveMeRandomString()
    (realHwDeviceId, dType, realDescription, secIndex)
  }

  def verifyDeviceWasCorrectlyAdded(
      deviceRoleName: String,
      hwDeviceId: String,
      apiConfigGroup: GroupResourceRepresentation,
      deviceConfigGroup: GroupResourceRepresentation,
      userGroupName: String,
      listGroupsId: List[String],
      description: String,
      additionalAttributes: Option[Map[String, List[String]]] = None,
      maybePassword: Option[String] = None
  )(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = Util.attributesToMap(deviceKc.toRepresentation.getAttributes)
    val apiAttributes: Map[String, List[String]] = apiConfigGroup.getAttributesScala
    val newApiAttributes = updateApiConfigGroup(apiAttributes, maybePassword)
    println(apiAttributes)
    println(newApiAttributes)

    val deviceConfAttributes = deviceConfigGroup.getAttributesScala
    // check attributes
    val attributesShouldBe = newApiAttributes ++ deviceConfAttributes ++ additionalAttributes.getOrElse(Nil)
    deviceAttributes.toList.sortBy(_._1) shouldBe attributesShouldBe.toList.sortBy(_._1)
    // check group membership
    val deviceGroups = deviceKc.groups().asScala.toList
    val deviceGroupsId = deviceGroups map { x =>
      x.getId
    }
    val userGroupId = realm.groups().groups(userGroupName, 0, 1).get(0).getId
    val lGroupsId = apiConfigGroup.representation.getId :: deviceConfigGroup.representation.getId :: userGroupId :: listGroupsId
    deviceGroupsId.sortBy(x => x) shouldBe lGroupsId.sortBy(x => x)

    // check roles
    val deviceRole =
      deviceKc.roles().realmLevel().listEffective().asScala.toList
    deviceRole.exists(x => x.getName == deviceRoleName) shouldBe true

    // check normal infos
    deviceKc.toRepresentation.getLastName shouldBe description
    deviceKc.toRepresentation.getUsername shouldBe hwDeviceId.toLowerCase
  }

  /**
    * This method is here to update the apiConfigGroup to the one corresponding to the device with its password
    */
  def updateApiConfigGroup(apiConfigGroup: Map[String, List[String]], maybePassword: Option[String]): Map[String, List[String]] = {
    maybePassword match {
      case Some(password) =>

        import org.json4s.jackson.JsonMethods._
        val json = parse(apiConfigGroup.head._2.head)
        println(compact(render(json)))
        val newValue = json.replace("password" :: Nil, JString(password))
        val newList = List(compact(render(newValue)))
        Map(apiConfigGroup.head._1 -> newList)

      case None => apiConfigGroup
    }
  }

  def verifyDeviceWasCorrectlyAddedAdmin(deviceRoleName: String, hwDeviceId: String, apiConfigGroup: GroupResourceRepresentation,
      deviceConfigGroup: GroupResourceRepresentation, userGroupName: String, listGroupsId: List[String],
      description: String, provider: String, secondaryIndex: String = Elements.DEFAULT_FIRST_NAME)(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = deviceKc.toResourceRepresentation.getAttributesScala
    val apiAttributes = apiConfigGroup.getAttributesScala
    val deviceConfAttributes = deviceConfigGroup.getAttributesScala
    val providerGroup = GroupFactory.getByName(Util.getProviderGroupName(provider))
    val unclaimedDevicesGroup = GroupFactory.getByName(Elements.UNCLAIMED_DEVICES_GROUP_NAME)
    // check attributes
    deviceAttributes shouldBe (apiAttributes ++ deviceConfAttributes)
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

  def verifyDeviceWasCorrectlyClaimed(hwDeviceId: String, apiConfigGroup: GroupResourceRepresentation, ownerUsername: String,
      deviceConfigGroup: GroupResourceRepresentation, listGroupsId: List[String],
      description: String, provider: String, secondaryIndex: String = Elements.DEFAULT_FIRST_NAME, claimingTags: List[String] = List(""))(implicit realm: RealmResource): Unit = {
    val deviceTmp = realm.users().search(hwDeviceId).get(0)
    val deviceKc = realm.users().get(deviceTmp.getId)
    val deviceAttributes = deviceKc.toRepresentation.getAttributes.asScala.toMap
    val apiAttributes = apiConfigGroup.getAttributesScala
    val deviceConfAttributes = deviceConfigGroup.getAttributesScala
    val providerGroup = GroupFactory.getByName(Util.getProviderGroupName(provider))
    val userGroupId = realm.groups().groups(Util.getDeviceGroupNameFromUserName(ownerUsername), 0, 1).get(0)
    val userFirstClaimedGroup = GroupFactory.getByName(Util.getUserFirstClaimedName(ownerUsername))
    val providerClaimedGroup = GroupFactory.getByName(Util.getProviderClaimedDevicesName(provider))

    // check attributes
    val apiAttributeHead = deviceAttributes(Elements.ATTRIBUTES_API_GROUP_NAME).toArray.head.toString
    implicit val formats: DefaultFormats.type = DefaultFormats
    val json = parse(apiAttributeHead)
    val pwd = (json \ "password").extract[String]
    UUID.fromString(pwd) // will fail if not a uuid
    deviceAttributes(Elements.ATTRIBUTES_DEVICE_GROUP_NAME).toArray shouldBe deviceConfAttributes.head._2.toArray
    deviceAttributes(Elements.CLAIMING_TAGS_NAME).toArray() shouldBe claimingTags.toArray
    deviceAttributes(Elements.FIRST_CLAIMED_TIMESTAMP)

    // check group membership
    val deviceGroups = deviceKc.groups().asScala.toList
    logger.info("device groups: " + deviceGroups.map { g => g.getName + "-" + g.getId }.mkString(", "))
    val deviceGroupsId = deviceGroups map { x =>
      x.getId
    }
    val listGroupsShouldBe = List(userGroupId.getName, userFirstClaimedGroup.name, apiConfigGroup.name, deviceConfigGroup.name, providerGroup.name, providerClaimedGroup.name)
    logger.info(listGroupsShouldBe.mkString(", "))
    val lGroupsId = userGroupId.getId :: userFirstClaimedGroup.id :: apiConfigGroup.id :: deviceConfigGroup.id :: providerGroup.id :: providerClaimedGroup.id :: listGroupsId
    deviceGroupsId.sortBy(x => x) shouldBe lGroupsId.sortBy(x => x)

    // check normal infos
    deviceKc.toRepresentation.getLastName shouldBe description
    deviceKc.toRepresentation.getUsername shouldBe hwDeviceId.toLowerCase
    deviceKc.toRepresentation.getFirstName shouldBe secondaryIndex
  }

  /**
    * Create a simple user with random username, first name and last name.
    * Does not initialise his groups
    */
  def createSimpleUser()(implicit realmName: String): MemberResourceRepresentation = {
    def realm = Util.getRealm

    val username = giveMeRandomString()
    val lastName = giveMeRandomString()
    val firstName = giveMeRandomString()
    val userRep = new UserRepresentation
    userRep.setUsername(username)
    userRep.setLastName(lastName)
    userRep.setFirstName(firstName)
    val res = realm.users().create(userRep)
    realm.users().get(ApiUtil.getCreatedId(res)).toResourceRepresentation
  }

  def giveMeRandomString(size: Int = 32): String = {
    Random.alphanumeric.take(size).mkString
  }

  def giveMeRandomUUID: String = {
    java.util.UUID.randomUUID.toString
  }

  def giveMeASimpleUser(): SimpleUser = {
    val username = giveMeRandomString()
    val lastName = giveMeRandomString()
    val firstName = giveMeRandomString()
    SimpleUser("", username = username, lastname = lastName, firstname = firstName)
  }

  /**
    * Create a new user having DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME as well as
    * - creating a DEFAULT_USERNAME_OWN_DEVICES group
    * - creating a apiConfigGroup
    * - creating a deviceConfigGroup
    * - create the DEVICE and USER roles
    * - creating the device
    * @return the newly created device having
    *           - random hwDeviceID
    *           - type default_type
    *           - description: DEFAULT_DESCRIPTION
    */
  def createRandomDeviceFromEmptyKeycloak()(implicit realm: RealmResource): MemberResourceRepresentation = {
    // vals
    val userStruct = SimpleUser("", DEFAULT_USERNAME, DEFAULT_LASTNAME, DEFAULT_FIRSTNAME)

    val (hwDeviceId, deviceType, deviceDescription) = TestRefUtil.generateDeviceAttributes(description = DEFAULT_DESCRIPTION)

    val (userGroupName, apiConfigName, deviceConfName) = createGroupsName(userStruct.username, realmName, deviceType)
    println(createGroupsName(userStruct.username, realmName, deviceType)._2)

    val (attributeDConf, attributeApiConf) = (DEFAULT_MAP_ATTRIBUTE_D_CONF, DEFAULT_MAP_ATTRIBUTE_API_CONF)

    // create groups
    val (userGroup, apiConfigGroup, _) = createGroups(userGroupName)(attributeApiConf, apiConfigName)(attributeDConf, deviceConfName)
    println(apiConfigGroup.name)
    // create user
    val user: MemberResourceRepresentation = TestRefUtil.addUserToKC(userStruct)
    // make user join groups
    user.joinGroupById(userGroup.id)
    user.joinGroupById(apiConfigGroup.id)

    val listGroupsToJoinId = List()
    // create roles
    val userRole: RoleResource = createRoles()(realm)(1)
    user.resource.addRoles(List(userRole.toRepresentation))
    // create device and return device id
    DeviceFactory.createDevice(AddDevice(hwDeviceId, deviceDescription, deviceType, listGroupsToJoinId), user).toResourceRepresentation

  }

  /**
    * Call this method on an empty realm to initialise it with the
    * - DEVICE and USER role
    * - ApiConfigGroup and
    * - One fully created user : username, first and last name, assigned USER role and username_OWN_DEVICES group as well
    * as the ownership of the created device
    *
    */
  def initKeycloakDeviceUser(initKeycloakBuilder: InitKeycloakBuilder = defaultInitKeycloakBuilder)(implicit realm: RealmResource): InitKeycloakResponse = {

    // generate roles
    val roles: List[RoleResource] = initKeycloakBuilder.roles match {
      case Some(r) => createRoles(r)
      case None => Nil
    }

    // generate default groups (apiConfGroup, deviceConfGroup)
    val groups: List[GroupIsAndShould] = initKeycloakBuilder.defaultGroups match {
      case Some(defaultGroups) => defaultGroups.createGroups
      case None => Nil
    }

    // create user and their devices
    val createdUsersAndDevices: List[CreatedUserAndDevices] = initKeycloakBuilder.users match {
      case Some(usersDevices) => usersDevices.createUsersAndDevices
      case None => Nil
    }
    InitKeycloakResponse(createdUsersAndDevices, groups, roles)
  }

  /**
    * Create the specified roles in the realm and return them. Default on the DEVICE and USER roles.
    */
  def createRoles(roles: List[String] = List(Elements.DEVICE, Elements.USER))(implicit realm: RealmResource): List[RoleResource] = roles.map(createAndGetSimpleRole)

  def createGroupsName(username: String, realmName: String, deviceType: String): (String, String, String) = {
    val userGroupName = Util.getDeviceGroupNameFromUserName(username)
    val apiConfigName = Util.getApiConfigGroupName(realmName)
    val deviceConfName = Util.getDeviceConfigGroupName(deviceType)
    (userGroupName, apiConfigName, deviceConfName)
  }

  def createGroups(listGroups: GroupsWithAttribute)(implicit realm: RealmResource): List[GroupResourceRepresentation] = {
    listGroups.groups map { g => createGroupWithConf(g.attributes, g.groupName) }
  }

  def createGroups(userGroupName: String)(attributeApi: java.util.Map[String, java.util.List[String]], apiConfName: String)(attributeDevice: java.util.Map[String, java.util.List[String]], deviceConfName: String)(implicit realm: RealmResource): (GroupResourceRepresentation, GroupResourceRepresentation, GroupResourceRepresentation) = {
    val userGroup = TestRefUtil.createSimpleGroup(userGroupName)
    val apiConfigGroup = TestRefUtil.createGroupWithConf(attributeApi, apiConfName)
    val deviceConfigGroup = TestRefUtil.createGroupWithConf(attributeDevice, deviceConfName)
    (userGroup, apiConfigGroup, deviceConfigGroup)
  }
}

case class InitKeycloakResponse(usersResponse: List[CreatedUserAndDevices], groupsCreated: List[GroupIsAndShould], roles: List[RoleResource]) extends Elements {
  def getUser(userName: String): Option[CreatedUserAndDevices] = usersResponse.find(u => u.userResult.should.username == userName)
  def getGroup(groupName: String): Option[GroupIsAndShould] = groupsCreated.find(g => g.should.groupName == groupName)
  def getApiConfigGroup(implicit realmName: String): Option[GroupIsAndShould] = groupsCreated.find(g => g.should.groupName == Util.getApiConfigGroupName(realmName))
  def getDeviceGroup(deviceTypeName: String = DEFAULT_TYPE): Option[GroupIsAndShould] = groupsCreated.find(g => g.should.groupName == Util.getDeviceConfigGroupName(deviceTypeName))
  def getDefaultGroupsAttributesShould(deviceTypeName: String = DEFAULT_TYPE)(implicit realmName: String) = DefaultGroupsAttributes(getApiConfigGroup.get.should.attributeAsScala, getDeviceGroup().get.should.attributeAsScala)
}

case class DefaultGroupsAttributes(apiConfigGroupAttributes: Map[String, List[String]], deviceTypeGroupAttributes: Map[String, List[String]])

case class InitKeycloakBuilder(
    users: Option[UsersDevices],
    roles: Option[List[String]] = Option(List(Elements.DEVICE, Elements.USER)),
    defaultGroups: Option[GroupsWithAttribute]
) {
  def addUsers(newUsersDevices: List[UserDevices]): InitKeycloakBuilder = copy(users.map { ud => ud.addUserDevices(newUsersDevices) })
}

case class UserDevices(userShould: SimpleUser, maybeDevicesShould: Option[List[DeviceStub]], maybeGroupsToJoin: Option[List[String]] = None, maybeAttributes: Option[java.util.Map[String, java.util.List[String]]] = None, maybeRoles: Option[List[String]] = None)(implicit realmName: String) extends Elements {
  def createUserAndDevices(implicit realm: RealmResource): CreatedUserAndDevices = {
    val user = TestRefUtil.addUserToKC(userShould, maybeAttributes)
    val userGroup = TestRefUtil.createSimpleGroup(Util.getDeviceGroupNameFromUserName(userShould.username))
    val apiConfigGroup = GroupFactory.getByName(Util.getApiConfigGroupName(DEFAULT_REALM_NAME))(DEFAULT_REALM_NAME)
    user.joinGroupById(userGroup.id)
    user.joinGroupById(apiConfigGroup.id)
    maybeGroupsToJoin match {
      case Some(groups) => user.joinNewGroupsByName(groups)
      case None =>
    }
    maybeRoles match {
      case Some(roles) =>
        val createdRoles = TestRefUtil.createRoles(roles).map(_.toRepresentation)
        user.resource.addRoles(TestRefUtil.getRole(Elements.USER).toRepresentation :: createdRoles)
      case None =>
        user.resource.addRoles(List(TestRefUtil.getRole(Elements.USER).toRepresentation))
    }

    val devicesCreated: List[DeviceIsAndShould] = maybeDevicesShould match {
      case Some(devices) => devices map {
        d => DeviceIsAndShould(DeviceFactory.createDevice(AddDevice(d.hwDeviceId, d.description, d.deviceType, List()), user).toResourceRepresentation, d)
      }
      case None => Nil
    }
    CreatedUserAndDevices(UserIsAndUserShould(user, userShould), devicesCreated)
  }
}

case class UsersDevices(usersDevices: List[UserDevices]) {
  def createUsersAndDevices(implicit realm: RealmResource): List[CreatedUserAndDevices] = {
    usersDevices.map(u => {
      println(s"creating user ${u.userShould.username}")
      u.createUserAndDevices
    })
  }

  def addUserDevices(newUsersDevices: List[UserDevices]): UsersDevices = copy(usersDevices = usersDevices ++ newUsersDevices)

}

case class GroupWithAttribute(groupName: String, attributes: java.util.Map[String, java.util.List[String]]) {
  def attributeAsScala: Map[String, List[String]] = Util.attributesToMap(attributes)
  def createGroup(implicit realm: RealmResource): GroupResourceRepresentation = createGroupWithConf(attributes, groupName)
}

case class GroupsWithAttribute(groups: List[GroupWithAttribute]) {
  def createGroups(implicit realm: RealmResource): List[GroupIsAndShould] = groups.map(g => GroupIsAndShould(g.createGroup, g))
}

case class CreatedUserAndDevices(userResult: UserIsAndUserShould, devicesResult: List[DeviceIsAndShould]) {
  /**
    * Helpful method used in numerous tests
    * @return the first device is
    */
  def getFirstDeviceIs: MemberResourceRepresentation = devicesResult.head.is
}

case class GroupIsAndShould(is: GroupResourceRepresentation, should: GroupWithAttribute)

case class DeviceIsAndShould(is: MemberResourceRepresentation, should: DeviceStub) {

}

case class UserIsAndUserShould(is: MemberResourceRepresentation, should: SimpleUser)
