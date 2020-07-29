package com.ubirch.webui.services

import java.util.Date

import com.ubirch.webui.models.graph.{ GraphClient, LastHash, UppState }
import com.ubirch.webui.TestRefUtil

import scala.concurrent.Future

class GraphClientMockOk extends GraphClient {

  def fakeLastHash(hwDeviceId: String, n: Int): List[LastHash] = {
    val r = for (_ <- 1 to n) yield {
      LastHash(hwDeviceId, Some(TestRefUtil.giveMeRandomString()), Some(new Date(scala.util.Random.nextInt(1000000))))
    }
    r.toList
  }

  /**
    * @return the number of UPPs that a device has created during the specified timeframe
    */
  override def getUPPs(from: Long, to: Long, hwDeviceId: String): Future[UppState] = ???

  /**
    * Will query the graph backend to find the last-hash property value contained on the graph
    * If it is not found, will return a failed LastHash structure
    *
    * @return a LastHash object containing the last hash (if found).
    */
  override def getLastHash(hwDeviceId: String): Future[LastHash] = Future.successful(fakeLastHash(hwDeviceId, 1).head)

  override def getLastNHashes(hwDeviceId: String, n: Int): Future[List[LastHash]] = Future.successful(fakeLastHash(hwDeviceId, n))
}
