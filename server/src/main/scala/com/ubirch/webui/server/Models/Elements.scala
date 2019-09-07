package com.ubirch.webui.server.Models

case class UpdateDevice(hwDeviceId: String,
                        ownerId: String,
                        apiConfig: String,
                        deviceConfig: String,
                        description: String,
                        deviceType: String,
                        groupList: List[String])
