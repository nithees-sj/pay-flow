package com.payflow.notifications.notification.infra;

import com.fasterxml.jackson.databind.*;
import com.payflow.common.api.PageResponse;
import com.payflow.common.contracts.PaymentEvent;
import com.payflow.common.error.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public NotificationRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public record Notification(
      UUID id,
      UUID paymentId,
      String type,
      String messageKey,
      JsonNode messageParameters,
      Instant createdAt,
      Instant readAt) {}

  public record Inbox(
      List<Notification> items,
      int page,
      int size,
      long totalElements,
      long totalPages,
      long unreadCount) {}

  public boolean receipt(PaymentEvent e) {
    return db.update(
            "INSERT INTO consumed_events(event_id,event_type,schema_version) VALUES(?,?,?) ON"
                + " CONFLICT DO NOTHING",
            e.eventId(),
            e.eventType(),
            e.schemaVersion())
        == 1;
  }

  public void insert(PaymentEvent e, UUID user) {
    var d = e.data();
    db.update(
        "INSERT INTO"
            + " notifications(id,event_id,user_id,payment_id,type,message_key,message_parameters)"
            + " VALUES(?,?,?,?,?,?,jsonb_build_object('amount',?::text,'currency',?::text,'direction',?::text))",
        UUID.randomUUID(),
        e.eventId(),
        user,
        d.paymentId(),
        e.eventType(),
        e.eventType(),
        d.amount(),
        d.currency(),
        user.equals(d.recipientUserId()) ? "INCOMING" : "OUTGOING");
  }

  public Inbox inbox(UUID user, int page, int size, boolean unread) {
    PageResponse.validate(page, size);
    String where = " WHERE user_id=?" + (unread ? " AND read_at IS NULL" : "");
    long total = db.queryForObject("SELECT count(*) FROM notifications" + where, Long.class, user);
    long count =
        db.queryForObject(
            "SELECT count(*) FROM notifications WHERE user_id=? AND read_at IS NULL",
            Long.class,
            user);
    var rows =
        db.query(
            "SELECT * FROM notifications"
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            this::map,
            user,
            size,
            (long) page * size);
    return new Inbox(rows, page, size, total, (total + size - 1) / size, count);
  }

  public Notification markRead(UUID id, UUID user) {
    if (db.update(
            "UPDATE notifications SET read_at=coalesce(read_at,now()) WHERE id=? AND user_id=?",
            id,
            user)
        == 0) throw new ApiException(404, "NOT_FOUND", "Notification not found");
    return db.query("SELECT * FROM notifications WHERE id=? AND user_id=?", this::map, id, user)
        .get(0);
  }

  private Notification map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    try {
      return new Notification(
          r.getObject("id", UUID.class),
          r.getObject("payment_id", UUID.class),
          r.getString("type"),
          r.getString("message_key"),
          json.readTree(r.getString("message_parameters")),
          r.getTimestamp("created_at").toInstant(),
          r.getTimestamp("read_at") == null ? null : r.getTimestamp("read_at").toInstant());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
