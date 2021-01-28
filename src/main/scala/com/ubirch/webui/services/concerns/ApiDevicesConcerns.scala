package com.ubirch.webui.services.concerns

import com.google.common.cache.{ CacheBuilder, CacheLoader, LoadingCache }
import com.ubirch.webui.models.keycloak.ReturnDeviceStubList

import java.util.concurrent.TimeUnit

object ApiDevicesConcerns {

  def userDevicesCache(init: String => Option[ReturnDeviceStubList]): LoadingCache[String, Option[ReturnDeviceStubList]] = CacheBuilder
    .newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build[String, Option[ReturnDeviceStubList]](
      new CacheLoader[String, Option[ReturnDeviceStubList]]() {
        override def load(k: String): Option[ReturnDeviceStubList] = init(k)
      }
    )

  final lazy val emptyUserDevicesCache = userDevicesCache(_ => None)

}

