package com.ubirch.webui.services

import java.util.Date

import com.ubirch.webui.models.graph.{ GraphClient, LastHash, UppState }
import com.ubirch.webui.TestRefUtil

import scala.concurrent.Future

class GraphClientMockOk extends GraphClient {

  def fakeLastHash(hwDeviceId: String, n: Int): List[LastHash] = {
    val r = for (_ <- 1 to n) yield {
      LastHash(hwDeviceId, Some(TestRefUtil.giveMeRandomString()), Some(new Date(scala.util.Random.nextInt(1000000).toLong)))
    }
    r.toList
  }

  def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState] = ???

  def getLastHashes(hwDeviceId: String, n: Int): Future[List[LastHash]] = Future.successful(fakeLastHash(hwDeviceId, n))
}
