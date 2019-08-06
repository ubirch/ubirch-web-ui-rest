package com.ubirch.webui.core.operations

import org.keycloak.admin.client.resource.{GroupResource, RealmResource}
import org.keycloak.representations.idm.GroupRepresentation

import collection.JavaConverters._
import scala.collection.mutable

class Groups(implicit realm: RealmResource) {

  def getGroupFromId(id: String): GroupResource = {
    realm.groups().group(id)
  }

  def getGroupFromName(name: String, max: Int = 1): mutable.Buffer[GroupRepresentation] = {
    realm.groups().groups(name, 0, max).asScala
  }


}
