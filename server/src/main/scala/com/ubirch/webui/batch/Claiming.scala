package com.ubirch.webui.batch

sealed trait Claiming {

  val value: Symbol

  def claim(ids: List[String], tags: String, prefix: Option[String])(implicit session: Session): ResponseStatus

}

object Claiming {

  val options: List[Claiming] = List(SIMClaiming)

  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[Claiming] = options.find(_.value.name == value)

  def fromSymbol(value: Symbol): Option[Claiming] = options.find(_.value == value)

}

object SIMClaiming extends Claiming {

  override val value: Symbol = 'sim_claiming

  override def claim(ids: List[String], tags: String, prefix: Option[String])(implicit session: Session): ResponseStatus = {
    ResponseStatus.Success(ids.size, ids.size, 0, Nil)
  }

}

case class ClaimRequest(claimType: Symbol, ids: List[String], tags: String, prefix: Option[String])
