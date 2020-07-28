package com.ubirch.webui.models.keycloak

import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.keycloak.member.UserFactory
import com.ubirch.webui.models.keycloak.util.BareKeycloakUtil._
import com.ubirch.webui.models.keycloak.util.MemberResourceRepresentation

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object TempScript extends LazyLogging {

  implicit val realmName = "ubirch-default-realm"

  def main(args: Array[String]): Unit = {
    val username = "benoit.george@ubirch.com"
    val user = UserFactory.getByUsername(username)
    var userDevices = user.getOwnDeviceGroup().getMembers.map(_.toResourceRepresentation).filter(_.isDevice)
    while (userDevices.nonEmpty) {
      processDevicesAsynch(userDevices, execute)
      logger.info("Looking for new devices")
      userDevices = user.getOwnDeviceGroup().getMembers.map(_.toResourceRepresentation).filter(_.isDevice)
    }
    logger.info("Finished")
  }

  def execute(d: MemberResourceRepresentation) = {
    if (d.representation.getLastName.toLowerCase == "dwefr") d.representation.delete
  }

  def processDevicesAsynch(devices: List[MemberResourceRepresentation], execute: MemberResourceRepresentation => Unit): Unit = {
    val partitionSize = 10
    val devicesPartitionned = devices.grouped(partitionSize).toList
    devicesPartitionned foreach { device =>
      val processesOfFutures = scala.collection.mutable.ListBuffer.empty[Future[Unit]]
      import scala.concurrent.ExecutionContext.Implicits.global
      device.foreach { edge =>
        val process = Future(execute(edge))
        processesOfFutures += process
      }
      val futureProcesses = Future.sequence(processesOfFutures)
      val latch = new CountDownLatch(1)
      futureProcesses.onComplete {
        case Success(_) =>
          latch.countDown()
        case Failure(e) =>
          logger.error("Something happened", e)
          latch.countDown()
      }
      latch.await()
      logger.info(s"FINISHED processing a batch of ${device.size} devices asynchronously")
    }
  }
}
