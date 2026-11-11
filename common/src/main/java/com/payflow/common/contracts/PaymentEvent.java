package com.payflow.common.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/** Versioned event contract. Unknown optional properties remain forward compatible. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String traceId,
    Data data) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(
      UUID paymentId,
      UUID transferId,
      String kind,
      UUID initiatedBy,
      UUID senderUserId,
      UUID recipientUserId,
      String amount,
      String currency,
      String status,
      String reasonCode) {}
}
