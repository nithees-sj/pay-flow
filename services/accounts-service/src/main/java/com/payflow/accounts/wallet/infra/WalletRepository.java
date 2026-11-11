package com.payflow.accounts.wallet.infra;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {
  private final JdbcTemplate db;

  public WalletRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Wallet(UUID id, UUID userId, String currency, String status) {}

  public void create(UUID id, UUID user) {
    db.update(
        "INSERT INTO wallets(id,user_id,currency,status) VALUES(?,?,'INR','PROVISIONING')",
        id,
        user);
  }

  public Optional<Wallet> byUser(UUID user) {
    return db
        .query(
            "SELECT id,user_id,currency,status FROM wallets WHERE user_id=?",
            (r, n) ->
                new Wallet(
                    r.getObject(1, UUID.class),
                    r.getObject(2, UUID.class),
                    r.getString(3),
                    r.getString(4)),
            user)
        .stream()
        .findFirst();
  }

  public List<Wallet> pending() {
    return db.query(
        "UPDATE wallets SET lease_until=now()+interval '30 seconds' WHERE id IN(SELECT id FROM"
            + " wallets WHERE status='PROVISIONING' AND next_attempt_at<=now() AND (lease_until IS"
            + " NULL OR lease_until<now()) ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT"
            + " 20) RETURNING id,user_id,currency,status",
        (r, n) ->
            new Wallet(
                r.getObject(1, UUID.class),
                r.getObject(2, UUID.class),
                r.getString(3),
                r.getString(4)));
  }

  public void active(UUID id) {
    db.update(
        "UPDATE wallets SET status='ACTIVE',updated_at=now(),lease_until=NULL,last_error_code=NULL"
            + " WHERE id=?",
        id);
  }

  public void retry(UUID id) {
    db.update(
        "UPDATE wallets SET"
            + " provisioning_attempts=provisioning_attempts+1,lease_until=NULL,next_attempt_at=now()+make_interval(secs=>least(60,power(2,least(provisioning_attempts,5))::int)),last_error_code='LEDGER_UNAVAILABLE'"
            + " WHERE id=? AND status='PROVISIONING'",
        id);
  }
}
