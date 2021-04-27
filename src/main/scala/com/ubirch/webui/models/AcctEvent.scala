package com.ubirch.webui.models

import java.nio.charset.StandardCharsets
import java.util.{ Date, UUID }

import com.ubirch.webui.batch.Batch
import com.ubirch.webui.kafka.GenericProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.concurrent.Future

case class AcctEvent(
    id: UUID,
    ownerId: UUID,
    identityId: Option[UUID],
    category: String,
    description: Option[String],
    token: Option[String],
    occurredAt: Date
) {
  def validate: Boolean = identityId.isDefined && description.isDefined
}

object AcctEvent {

  implicit val formats: Formats = Batch.formats

  def publishAcctEvent(acctEvent: AcctEvent): Future[RecordMetadata] = {
    GenericProducer.send(GenericProducer.ACCT_EVENT_PRODUCER_TOPIC, write(acctEvent).getBytes(StandardCharsets.UTF_8))
  }

}
