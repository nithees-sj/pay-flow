package com.payflow.notifications.notification.domain;

import com.payflow.common.contracts.PaymentEvent;
import com.payflow.common.money.Money;
import com.payflow.notifications.notification.infra.NotificationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository repo;

  public NotificationService(NotificationRepository repo) {
    this.repo = repo;
  }

  /** Commits the event receipt and every recipient notification together. */
  @Transactional
  public boolean accept(PaymentEvent event) {
    validate(event);
    if (!repo.receipt(event)) return false;
    var users = new LinkedHashSet<UUID>();
    var data = event.data();
    users.add(data.initiatedBy());
    if (data.status().equals("COMPLETED")) users.add(data.recipientUserId());
    for (UUID user : users) repo.insert(event, user);
    return true;
  }

  private void validate(PaymentEvent event) {
    if (event == null
        || event.schemaVersion() != 1
        || event.eventId() == null
        || event.occurredAt() == null
        || event.data() == null)
      throw new IllegalArgumentException("Unsupported or incomplete event");
    UUID.fromString(event.traceId());
    var d = event.data();
    if (d.paymentId() == null
        || d.transferId() == null
        || d.initiatedBy() == null
        || !Set.of("PAYMENT", "TEST_TOPUP").contains(d.kind())
        || !"INR".equals(d.currency())
        || !Set.of("COMPLETED", "FAILED", "DECLINED").contains(d.status())
        || !("payment." + d.status().toLowerCase(Locale.ROOT)).equals(event.eventType()))
      throw new IllegalArgumentException("Invalid payment event");
    long amount = Money.parse(d.amount());
    if (d.status().equals("COMPLETED")
        && (amount <= 0 || d.recipientUserId() == null || d.reasonCode() != null))
      throw new IllegalArgumentException("Invalid completed event");
    if (!d.status().equals("COMPLETED") && d.reasonCode() == null)
      throw new IllegalArgumentException("Missing failure reason");
    if (amount <= 0 && !(d.status().equals("DECLINED") && "INVALID_AMOUNT".equals(d.reasonCode())))
      throw new IllegalArgumentException("Invalid event amount");
    if (d.kind().equals("PAYMENT") && !d.initiatedBy().equals(d.senderUserId()))
      throw new IllegalArgumentException("Invalid sender");
    if (d.kind().equals("TEST_TOPUP")
        && (d.senderUserId() != null || !d.initiatedBy().equals(d.recipientUserId())))
      throw new IllegalArgumentException("Invalid funding participants");
  }
}
