package com.payflow.payments.risk.infra;

import com.payflow.payments.payment.domain.Payment;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RiskRepository {
  private final JdbcTemplate db;

  public RiskRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Counts(long minute, long daily) {}

  public void lock(UUID user) {
    db.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,1))", Object.class, user.toString());
  }

  public String decision(UUID id) {
    return db.queryForObject("SELECT risk_decision FROM payments WHERE id=?", String.class, id);
  }

  public void attempt(Payment p) {
    db.update(
        "INSERT INTO risk_attempts(payment_id,user_id,amount_minor,currency) VALUES(?,?,?,?) ON"
            + " CONFLICT DO NOTHING",
        p.id(),
        p.initiatedBy(),
        p.amount(),
        p.currency());
  }

  public Counts counts(UUID user, long cap) {
    return db.queryForObject(
        "SELECT count(*) FILTER(WHERE"
            + " attempted_at>=date_trunc('minute',now())),least(coalesce(sum(amount_minor::numeric),0),?)::bigint"
            + " FROM risk_attempts WHERE user_id=? AND attempted_at>=date_trunc('day',now() AT TIME"
            + " ZONE 'UTC') AT TIME ZONE 'UTC'",
        (r, n) -> new Counts(r.getLong(1), r.getLong(2)),
        cap,
        user);
  }

  public void decision(UUID id, String decision) {
    db.update(
        "UPDATE payments SET risk_decision=? WHERE id=? AND status='CREATED' AND risk_decision IS"
            + " NULL",
        decision,
        id);
  }
}
