package com.ubirch.webui.models.keycloak.member

sealed trait DeviceCreationState {
  def hwDeviceId: String
  def state: String
  def toJson: String
}

case class DeviceCreationSuccess(hwDeviceId: String)
  extends DeviceCreationState {
  val state = "ok"
  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val json = hwDeviceId -> ("state" -> "ok")
    compact(render(json))
  }
}

case class DeviceCreationFail(hwDeviceId: String, error: String, errorCode: Int)
  extends DeviceCreationState {
  val state = "notok"

  def toJson: String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    val jsonError =
      hwDeviceId ->
        ("state" -> "notok") ~
        ("error" -> error) ~
        ("errorCode" -> errorCode)
    compact(render(jsonError))
  }
}
