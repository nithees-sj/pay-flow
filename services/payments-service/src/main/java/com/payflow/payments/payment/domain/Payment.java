package com.payflow.payments.payment.domain;

import java.time.Instant;
import java.util.UUID;

public record Payment(
    UUID id,
    UUID transferId,
    String kind,
    UUID initiatedBy,
    UUID senderUserId,
    UUID recipientUserId,
    String recipientEmail,
    UUID fromAccount,
    UUID toAccount,
    long amount,
    String currency,
    String note,
    PaymentStatus status,
    String reasonCode,
    String riskDecision,
    Instant createdAt,
    Instant updatedAt,
    long version,
    String traceId) {}
