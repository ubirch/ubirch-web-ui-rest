package com.ubirch.webui

import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil.RichUserResource
import com.ubirch.webui.models.{ ApiUtil, Elements }
import com.ubirch.webui.models.keycloak.util.{ QuickActions, Util }
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }

import java.util.UUID
import scala.collection.JavaConverters._

object CreateDevices extends App {

  val groupId = "0e9ff905-8eef-4aae-863a-25c6495e6690" //Please change the group id
  val realmName = "test-realm"

  (0 to 100).foreach { _ =>
    val deviceRepresentation = new UserRepresentation
    val deviceId = UUID.randomUUID()
    deviceRepresentation.setEnabled(true)
    deviceRepresentation.setUsername(deviceId.toString)
    deviceRepresentation.setLastName("Default description for the devices")
    deviceRepresentation.setFirstName("Firstname")

    val deviceCredential = new CredentialRepresentation
    deviceCredential.setValue("devicePassword")
    deviceCredential.setTemporary(false)
    deviceCredential.setType(CredentialRepresentation.PASSWORD)

    deviceRepresentation.setCredentials(Util.singleTypeToStupidJavaList[CredentialRepresentation](deviceCredential))

    deviceRepresentation.setAttributes(Map("csc" -> List("DELHIS").asJava).asJava)

    val realm = Util.getRealm(realmName)
    val deviceKcId = ApiUtil.getCreatedId(realm.users().create(deviceRepresentation))
    val newDevice = QuickActions.quickSearchId(deviceKcId)(realmName)
    newDevice.addRoles(List(Util.getRole(Elements.DEVICE)(realmName).toRepresentation))

    newDevice.joinGroup(groupId)
  }
}
