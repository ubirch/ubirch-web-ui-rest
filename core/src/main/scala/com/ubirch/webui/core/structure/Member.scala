package com.ubirch.webui.core.structure

import com.ubirch.webui.core.structure.group.Group
import org.keycloak.representations.idm.UserRepresentation

trait Member {
  def keyCloakId: String
  def username: String
  def firstName: String
  def lastName: String
  def groups: List[Group]
  def roles: List[String]

  def getMemberRepresentation: UserRepresentation
}
