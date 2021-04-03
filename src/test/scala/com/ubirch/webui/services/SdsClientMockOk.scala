package com.ubirch.webui.services

import com.ubirch.webui.models.sds.SimpleDataServiceClient

import scala.concurrent.Future

class SdsClientMockOk extends SimpleDataServiceClient {
  /**
    * Return the last _number_ values stored in the sds by the thing having the device id _hwDeviceId_
    * Auth by hwDeviceId and password
    * Use ascii password, the method will convert it to base64 by itself
    */
  override def getLastValues(hwDeviceId: String, password: String, number: Int): Future[String] = ???
}
