package com.payflow.payments.payment.api;

import com.payflow.payments.payment.domain.Payment;
import java.time.Instant;
import java.util.*;

public record PaymentView(
    UUID id,
    UUID transferId,
    String kind,
    String direction,
    UUID senderUserId,
    UUID recipientUserId,
    String recipientEmail,
    String amount,
    String currency,
    String note,
    String status,
    String reasonCode,
    Instant createdAt,
    Instant updatedAt,
    List<Transition> transitions) {
  public record Transition(
      String fromStatus, String toStatus, String reasonCode, Instant occurredAt) {}

  public static PaymentView of(Payment p, UUID viewer, List<Transition> transitions) {
    return new PaymentView(
        p.id(),
        p.transferId(),
        p.kind(),
        viewer.equals(p.recipientUserId()) ? "INCOMING" : "OUTGOING",
        p.senderUserId(),
        p.recipientUserId(),
        p.recipientEmail(),
        Long.toString(p.amount()),
        p.currency(),
        p.note(),
        p.status().name(),
        p.reasonCode(),
        p.createdAt(),
        p.updatedAt(),
        transitions);
  }
}
