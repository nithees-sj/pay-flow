package com.payflow.ledger.wallet.infra;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  private final JdbcTemplate db;

  public AccountRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Account(
      UUID id, UUID owner, String kind, String currency, long balance, Instant updatedAt) {}

  public void create(UUID id, UUID owner, String currency) {
    db.update(
        "INSERT INTO ledger_accounts(id,owner_user_id,kind,currency) VALUES(?,?,'USER',?) ON"
            + " CONFLICT DO NOTHING",
        id,
        owner,
        currency);
  }

  public Optional<Account> get(UUID id) {
    return db.query("SELECT * FROM ledger_accounts WHERE id=?", AccountRepository::map, id).stream()
        .findFirst();
  }

  public Optional<Account> owned(UUID user) {
    return db
        .query(
            "SELECT * FROM ledger_accounts WHERE owner_user_id=? AND currency='INR'",
            AccountRepository::map,
            user)
        .stream()
        .findFirst();
  }

  public List<Account> lock(UUID from, UUID to) {
    return db.query(
        "SELECT * FROM ledger_accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",
        AccountRepository::map,
        from,
        to);
  }

  public void balance(UUID id, long amount) {
    db.update("UPDATE ledger_accounts SET balance_minor=?,updated_at=now() WHERE id=?", amount, id);
  }

  private static Account map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new Account(
        r.getObject("id", UUID.class),
        r.getObject("owner_user_id", UUID.class),
        r.getString("kind"),
        r.getString("currency"),
        r.getLong("balance_minor"),
        r.getTimestamp("updated_at").toInstant());
  }
}
