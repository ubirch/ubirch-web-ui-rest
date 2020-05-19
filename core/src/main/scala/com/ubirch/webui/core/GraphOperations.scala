package com.ubirch.webui.core

import com.ubirch.webui.core.config.ConfigBase
import com.ubirch.webui.core.structure.member.{ DeviceFactory, UppState, User }

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }

object GraphOperations extends ConfigBase {

  /**
    *
    * @param hwDeviceIds
    * @param from
    * @param to
    * @param realmName
    * @return
    */
  def bulkGetUpps(user: User, hwDeviceIds: List[String], from: Long, to: Long)(implicit realmName: String): List[UppState] = {
    val processOfFutures =
      scala.collection.mutable.ListBuffer.empty[Future[UppState]]
    import scala.concurrent.ExecutionContext.Implicits.global
    hwDeviceIds.foreach { hwDeviceID =>
      val process = Future {
        DeviceFactory.getByHwDeviceId(hwDeviceID) match {
          case Left(_) => UppState(hwDeviceID, from, to, -1)
          case Right(device) => if (device.isUserAuthorizedBoolean(user)) {
            device.getUPPs(from, to)
          } else {
            UppState(hwDeviceID, from, to, -1)
          }
        }
      }
      processOfFutures += process
    }
    val futureProcesses: Future[ListBuffer[UppState]] =
      Future.sequence(processOfFutures)

    futureProcesses.onComplete {
      case Success(success) =>
        success
      case Failure(error) =>
        throw error
        scala.collection.mutable.ListBuffer.empty[Future[UppState]]
    }
    //@TODO please fix that blocking code
    Await.result(futureProcesses, timeToWait.second).toList

  }
}
