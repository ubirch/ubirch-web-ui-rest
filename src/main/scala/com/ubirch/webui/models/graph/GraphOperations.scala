package com.ubirch.webui.models.graph

import com.ubirch.webui.config.ConfigBase
import com.ubirch.webui.models.keycloak.member.{ DeviceFactory, UppState }
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
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
  def bulkGetUpps(user: UserRepresentation, hwDeviceIds: List[String], from: Long, to: Long)(implicit realmName: String): Future[List[UppState]] = {
    val processOfFutures =
      scala.collection.mutable.ListBuffer.empty[Future[UppState]]
    import scala.concurrent.ExecutionContext.Implicits.global
    hwDeviceIds.foreach { hwDeviceID =>
      val process = Future {
        DeviceFactory.getByHwDeviceId(hwDeviceID) match {
          case Left(_) => UppState(hwDeviceID, from, to, -1)
          case Right(device) => if (device.isUserAuthorizedQuick(user)) {
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
        success.toList
      case Failure(_) =>
        scala.collection.mutable.ListBuffer.empty[Future[UppState]]
    }
    for {
      processed <- futureProcesses
    } yield {
      processed.toList
    }

  }
}
