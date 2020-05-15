package com.ubirch.webui.core.structure.util

import com.ubirch.webui.core.structure.group.{ Group, GroupFactory }
import com.ubirch.webui.core.structure.Elements
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

object Converter {

  def groupsRepresentationToGroup(groups: List[GroupRepresentation])(implicit realmName: String): List[Group] = {
    groups map { g =>
      GroupFactory.getById(g.getId)
    }
  }

  def attributesToMap(attributes: java.util.Map[String, java.util.List[String]]): Map[String, List[String]] = {
    attributes.asScala.toMap map { keyValue =>
      keyValue._1 -> keyValue._2.asScala.toList
    }
  }

  /**
    * In order to sole the keycloak fuzzy search username lookup, it was decided that the device username will
    * be renamed device_<uuid>_device. This function is an helper that achieve this transformation.
    * @param uuid the uuid to transform
    * @return the transformed uuid
    */
  def transformUuidToDeviceUsername(uuid: String): String = {
    Elements.DEVICE_PREFIX + uuid + Elements.DEVICE_SUFFIX
  }

  /**
    * Will transform a device_<uuid>_device string into uuid.
    * @param transformedUuid string to transform back
    * @return An option that contains the transformed string if the operation was successful.
    */
  def untransformUuidToDeviceUsername(transformedUuid: String): Option[String] = {

    val canStringBeTransformedBack = transformedUuid.take(Elements.DEVICE_PREFIX.length) == Elements.DEVICE_PREFIX && transformedUuid.takeRight(Elements.DEVICE_SUFFIX.length) == Elements.DEVICE_SUFFIX

    if (canStringBeTransformedBack) {
      val beginingUuid = Elements.DEVICE_PREFIX.length
      val endUUID = transformedUuid.length - Elements.DEVICE_SUFFIX.length
      Some(transformedUuid.substring(beginingUuid, endUUID))
    } else {
      None
    }
  }
}
