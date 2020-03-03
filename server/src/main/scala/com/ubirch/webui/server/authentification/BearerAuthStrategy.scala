package com.ubirch.webui.server.authentification

import java.util.Locale

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.structure.member.{ User, UserFactory }
import com.ubirch.webui.core.structure.{ TokenProcessor, UserInfo }
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import org.scalatra.auth.strategy.BasicAuthSupport
import org.scalatra.auth.{ ScentryConfig, ScentryStrategy, ScentrySupport }
import org.scalatra.{ ScalatraBase, Unauthorized }

import scala.language.implicitConversions

/**
  * Authentication support for routes
  */
trait AuthenticationSupport extends ScentrySupport[UserInfo] with BasicAuthSupport[UserInfo] with LazyLogging {
  self: ScalatraBase =>

  protected def fromSession: PartialFunction[String, UserInfo] = {
    case i: String =>
      val splicedString = i.split(";")
      UserInfo(splicedString.head, splicedString(1), splicedString(2))
  }

  protected def toSession: PartialFunction[UserInfo, String] = {
    case usr: UserInfo => usr.realmName + ";" + usr.id + ";" + usr.userName
  }

  val realm = "Bearer Authentication"
  protected val scentryConfig: ScentryConfiguration = new ScentryConfig {}.asInstanceOf[ScentryConfiguration]

  override protected def configureScentry: Unit = {
    scentry.unauthenticated {
      scentry.strategies("Bearer").unauthenticated()
    }
  }

  override protected def registerAuthStrategies: Unit = {
    scentry.register("Bearer", app => new BearerStrategy(app, realm))
  }

  // verifies if the request is a Bearer request
  protected def auth()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[UserInfo] = {
    val baReq = new BearerAuthRequest(request)
    if (!baReq.providesAuth) {
      logger.info("Auth: Unauthenticated")
      halt(401, "Unauthenticated")
    }
    if (!baReq.isBearerAuth) {
      logger.info("Auth: Bad Request")
      halt(400, "Bad Request")
    }
    scentry.authenticate("Bearer")
  }

  def whenAdmin(action: (UserInfo, User) => Any): Any = {
    val userInfo = auth().get
    val user = UserFactory.getByUsername(userInfo.userName)(userInfo.realmName)
    if (user.isAdmin) {
      action(userInfo, user)
    } else {
      halt(Unauthorized())
    }
  }

}

class BearerStrategy(protected override val app: ScalatraBase, realm: String) extends ScentryStrategy[UserInfo]
  with LazyLogging {

  implicit def request2BearerAuthRequest(r: HttpServletRequest): BearerAuthRequest = new BearerAuthRequest(r)

  // TODO: remove that
  protected def validate(userName: String, password: String): Option[UserInfo] = {
    None
  }

  protected def getUserId(user: UserInfo): String = user.id

  override def isValid(implicit request: HttpServletRequest): Boolean = request.isBearerAuth && request.providesAuth

  // catches the case that we got none user
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app halt Unauthorized()
  }

  // overwrite required authentication request
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[UserInfo] = validate(request.token)

  protected def validate(token: String): Option[UserInfo] = {
    logger.debug("token: " + token)

    val opt = Option(TokenProcessor.validateToken(token))
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

