package com.ubirch.webui.core.operations.refactoring

import com.ubirch.webui.core.operations.Utils
import org.keycloak.admin.client.resource.UserResource

/*
Describe a member
 */
abstract class Member(val kcId: String)(implicit val realmName: String) {
  val userName: String
  val firstName: String
  val lastName: String
  val groups: List[String] // TODO: modify
  lazy val memberResource: UserResource = Utils.getKCUserFromId(kcId)


}
