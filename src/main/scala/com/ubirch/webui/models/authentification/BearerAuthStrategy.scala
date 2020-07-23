package com.ubirch.webui.models.authentification

import java.util.Locale

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.models.keycloak.{ TokenProcessor, UserInfo }
import com.ubirch.webui.models.keycloak.member._
import com.ubirch.webui.models.keycloak.member.MemberType.MemberType
import com.ubirch.webui.models.keycloak.util.MemberResourceRepresentation
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import org.scalatra.{ ScalatraBase, Unauthorized }
import org.scalatra.auth.{ ScentryConfig, ScentryStrategy, ScentrySupport }
import org.scalatra.auth.strategy.BasicAuthSupport

import scala.language.implicitConversions

/**
  * Authentication support for routes
  */
trait AuthenticationSupport extends ScentrySupport[(UserInfo, MemberType)] with BasicAuthSupport[(UserInfo, MemberType)] with LazyLogging {
  self: ScalatraBase =>

  protected def fromSession: PartialFunction[String, (UserInfo, MemberType)] = {
    case i: String =>
      val splicedString = i.split(";")
      (UserInfo(splicedString.head, splicedString(1), splicedString(2)), MemberType.User)
  }

  protected def toSession: PartialFunction[(UserInfo, MemberType), String] = {
    case usr: (UserInfo, MemberType) => usr._1.realmName + ";" + usr._1.id + ";" + usr._1.userName
  }

  val realm = "Bearer Authentication"
  protected val scentryConfig: ScentryConfiguration = new ScentryConfig {}.asInstanceOf[ScentryConfiguration]

  override protected def configureScentry: Unit = {
    scentry.unauthenticated {
      scentry.strategies("Bearer").unauthenticated()
    }
  }

  override protected def registerAuthStrategies: Unit = {
    scentry.register("Bearer", app => new BearerStrategy(app))
  }

  // verifies if the request is a Bearer request
  // None if auth invalid
  protected def auth()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[(UserInfo, MemberType)] = {
    val baReq: BearerAuthRequest = new BearerAuthRequest(request)
    if (!baReq.providesAuth) {
      logger.info("Auth: Unauthenticated")
      halt(401, "Unauthenticated")
    }
    if (!baReq.isBearerAuth) {
      logger.info("Auth: Bad Request")
      halt(400, "Bad Request")
    }
    val res = scentry.authenticate("Bearer")
    res
  }

  def whenAdmin(action: (UserInfo, User) => Any): Any = {
    auth() match {
      case Some(userInfo) =>
        if (userInfo._2 == MemberType.User) {
          val user = UserFactory.getByUsername(userInfo._1.userName)(userInfo._1.realmName)
          if (user.isAdmin) {
            action(userInfo._1, user)
          } else {
            halt(Unauthorized("Only admin user can do this operation. Please get in touch with Ubirch"))
          }
        } else halt(Unauthorized("logged in as a device when only a user can be logged as"))
      case None => halt(Unauthorized("Error while logging in"))
    }
  }

  def whenLoggedInAsUser(action: (UserInfo, User) => Any): Any = {
    auth() match {
      case Some(userInfo) =>
        if (userInfo._2 == MemberType.User) {
          val user = UserFactory.getByUsername(userInfo._1.userName)(userInfo._1.realmName)
          action(userInfo._1, user)
        } else halt(Unauthorized("logged in as a device when only a user can be logged as"))
      case None => halt(Unauthorized("Error while logging in"))
    }
  }

  /**
    * Does not fetch the user from the database
    * @param action what will be executed
    * @return
    */
  def whenLoggedInAsUserNoFetch(action: UserInfo => Any): Any = {
    auth() match {
      case Some(userInfo) =>
        if (userInfo._2 == MemberType.User) {
          action(userInfo._1)
        } else halt(Unauthorized("logged in as a device when only a user can be logged as"))
      case None => halt(Unauthorized("Error while logging in"))
    }
  }

  def whenLoggedInAsDevice(action: (UserInfo, MemberResourceRepresentation) => Any): Any = {
    auth() match {
      case Some(userInfo) =>
        if (userInfo._2 == MemberType.Device) {
          DeviceFactory.getByHwDeviceIdQuick(userInfo._1.userName)(userInfo._1.realmName) match {
            case Left(_) => halt(Unauthorized("did not find device"))
            case Right(value) => action(userInfo._1, value)
          }
        } else halt(Unauthorized("logged in as a user when only a device can be logged as"))
      case None => halt(Unauthorized("Error while logging in"))
    }
  }

}

class BearerStrategy(protected override val app: ScalatraBase) extends ScentryStrategy[(UserInfo, MemberType)]
  with LazyLogging {

  implicit def request2BearerAuthRequest(r: HttpServletRequest): BearerAuthRequest = new BearerAuthRequest(r)

  protected def getUserId(user: UserInfo): String = user.id

  override def isValid(implicit request: HttpServletRequest): Boolean = request.isBearerAuth && request.providesAuth

  // catches the case that we got none user
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
    app halt Unauthorized("Unauthenticated")
  }

  // overwrite required authentication request
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[(UserInfo, MemberType)] = validate(request.token)

  protected def validate(token: String): Option[(UserInfo, MemberType)] = {
    logger.debug("token: " + token)

    val opt = TokenProcessor.validateToken(token)
    logger.debug("option token= " + opt.getOrElse("not valid").toString)
    opt

  }
}

class BearerAuthRequest(r: HttpServletRequest) {

  private val AUTHORIZATION_KEYS = List("Authorization", "HTTP_AUTHORIZATION", "X-HTTP_AUTHORIZATION", "X_HTTP_AUTHORIZATION")

  def parts: List[String] = authorizationKey map {
    r.getHeader(_).split(" ", 2).toList
  } getOrElse Nil

  def scheme: Option[String] = parts.headOption.map(sch => sch.toLowerCase(Locale.ENGLISH))

  def token: String = parts.lastOption getOrElse ""

  private def authorizationKey = AUTHORIZATION_KEYS.find(r.getHeader(_) != null)

  def isBearerAuth: Boolean = (false /: scheme) { (_, sch) => sch == "bearer" }

  def providesAuth: Boolean = authorizationKey.isDefined

}

