package com.payflow.payments.payment.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.api.PageResponse;
import com.payflow.common.contracts.PaymentEvent;
import com.payflow.common.error.ApiException;
import com.payflow.payments.payment.api.PaymentView;
import com.payflow.payments.payment.domain.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PaymentRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public PaymentRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public record Claim(UUID id, long version) {}

  public Payment get(UUID id) {
    return db.query("SELECT * FROM payments WHERE id=?", PaymentRepository::map, id).stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Payment not found"));
  }

  public PaymentView visible(UUID id, UUID user) {
    var p = get(id);
    if (!p.initiatedBy().equals(user)
        && !(p.status() == PaymentStatus.COMPLETED && user.equals(p.recipientUserId())))
      throw new ApiException(404, "NOT_FOUND", "Payment not found");
    return view(p, user);
  }

  public PaymentView view(Payment p, UUID viewer) {
    return PaymentView.of(
        p,
        viewer,
        db.query(
            "SELECT from_status,to_status,reason_code,occurred_at FROM payment_transitions WHERE"
                + " payment_id=? ORDER BY occurred_at,id",
            (r, n) ->
                new PaymentView.Transition(
                    r.getString(1), r.getString(2), r.getString(3), r.getTimestamp(4).toInstant()),
            p.id()));
  }

  public PageResponse<PaymentView> history(
      UUID user, int page, int size, String status, String kind) {
    PageResponse.validate(page, size);
    if (status != null) PaymentStatus.valueOf(status);
    if (kind != null && !Set.of("PAYMENT", "TEST_TOPUP").contains(kind))
      throw new IllegalArgumentException("Invalid kind");
    String where = " WHERE (initiated_by=? OR(recipient_user_id=? AND status='COMPLETED'))";
    var args = new ArrayList<Object>(List.of(user, user));
    if (status != null) {
      where += " AND status=?";
      args.add(status);
    }
    if (kind != null) {
      where += " AND kind=?";
      args.add(kind);
    }
    long total =
        db.queryForObject("SELECT count(*) FROM payments" + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var items =
        db
            .query(
                "SELECT * FROM payments"
                    + where
                    + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                PaymentRepository::map,
                args.toArray())
            .stream()
            .map(p -> view(p, user))
            .toList();
    return PageResponse.of(items, page, size, total);
  }

  public boolean resolve(Claim claim, UUID sender, UUID recipient, UUID from, UUID to) {
    return db.update(
            "UPDATE payments SET"
                + " sender_user_id=?,recipient_user_id=?,from_account_id=?,to_account_id=?,updated_at=now()"
                + " WHERE id=? AND status='CREATED' AND version=?",
            sender,
            recipient,
            from,
            to,
            claim.id(),
            claim.version())
        == 1;
  }

  /** Terminal state, transition and event are a single database commit. */
  @Transactional
  public boolean finish(Claim claim, PaymentStatus status, String reason) {
    var p =
        db.query("SELECT * FROM payments WHERE id=? FOR UPDATE", PaymentRepository::map, claim.id())
            .get(0);
    if (p.status() != PaymentStatus.CREATED || p.version() != claim.version()) return false;
    p.status().requireTransitionTo(status);
    db.update(
        "UPDATE payments SET"
            + " status=?,reason_code=?,updated_at=now(),completed_at=now(),lease_until=NULL WHERE"
            + " id=?",
        status.name(),
        reason,
        p.id());
    db.update(
        "INSERT INTO payment_transitions(id,payment_id,from_status,to_status,reason_code)"
            + " VALUES(?,?,'CREATED',?,?)",
        UUID.randomUUID(),
        p.id(),
        status.name(),
        reason);
    var completed = get(p.id());
    UUID event = UUID.randomUUID();
    String type = "payment." + status.name().toLowerCase(Locale.ROOT);
    var payload =
        new PaymentEvent(
            event,
            type,
            1,
            completed.updatedAt(),
            p.traceId(),
            new PaymentEvent.Data(
                p.id(),
                p.transferId(),
                p.kind(),
                p.initiatedBy(),
                p.senderUserId(),
                p.recipientUserId(),
                Long.toString(p.amount()),
                p.currency(),
                status.name(),
                reason));
    try {
      db.update(
          "INSERT INTO"
              + " outbox_events(event_id,payment_id,event_type,schema_version,partition_key,payload)"
              + " VALUES(?,?,?,1,?,?::jsonb)",
          event,
          p.id(),
          type,
          p.id().toString(),
          json.writeValueAsString(payload));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    return true;
  }

  public void defer(Claim claim) {
    db.update(
        "UPDATE payments SET"
            + " lease_until=NULL,reconcile_attempts=reconcile_attempts+1,next_reconcile_at=now()+make_interval(secs=>least(60,power(2,least(reconcile_attempts,5))::int)),updated_at=now()"
            + " WHERE id=? AND status='CREATED' AND version=?",
        claim.id(),
        claim.version());
  }

  @Transactional
  public List<Claim> claimDue() {
    return db.query(
        "UPDATE payments SET lease_until=now()+interval '30 seconds',version=version+1 WHERE id"
            + " IN(SELECT id FROM payments WHERE status='CREATED' AND next_reconcile_at<=now() AND"
            + " (lease_until IS NULL OR lease_until<now()) ORDER BY next_reconcile_at FOR UPDATE"
            + " SKIP LOCKED LIMIT 10) RETURNING id,version",
        (r, n) -> new Claim(r.getObject(1, UUID.class), r.getLong(2)));
  }

  public static Payment map(ResultSet r, int n) throws SQLException {
    return new Payment(
        r.getObject("id", UUID.class),
        r.getObject("transfer_id", UUID.class),
        r.getString("kind"),
        r.getObject("initiated_by", UUID.class),
        r.getObject("sender_user_id", UUID.class),
        r.getObject("recipient_user_id", UUID.class),
        r.getString("recipient_email"),
        r.getObject("from_account_id", UUID.class),
        r.getObject("to_account_id", UUID.class),
        r.getLong("amount_minor"),
        r.getString("currency"),
        r.getString("note"),
        PaymentStatus.valueOf(r.getString("status")),
        r.getString("reason_code"),
        r.getString("risk_decision"),
        r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("updated_at").toInstant(),
        r.getLong("version"),
        r.getString("trace_id"));
  }
}
