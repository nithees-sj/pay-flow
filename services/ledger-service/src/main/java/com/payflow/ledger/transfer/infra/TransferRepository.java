package com.payflow.ledger.transfer.infra;

import com.payflow.common.api.PageResponse;
import com.payflow.common.contracts.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {
  private final JdbcTemplate db;

  public TransferRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Stored(TransferOutcome outcome, String requestHash) {}

  public record Entry(
      UUID id,
      UUID transferId,
      String direction,
      String amount,
      String currency,
      Instant createdAt) {}

  public void lockIdentity(UUID transfer) {
    db.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, transfer.toString());
  }

  public Optional<Stored> find(UUID id) {
    return db
        .query(
            "SELECT * FROM transfers WHERE transfer_id=?",
            (r, n) ->
                new Stored(
                    new TransferOutcome(id, r.getString("status"), r.getString("failure_code")),
                    r.getString("request_hash")),
            id)
        .stream()
        .findFirst();
  }

  public void outcome(TransferCommand c, long amount, String hash, String status, String reason) {
    db.update(
        "INSERT INTO"
            + " transfers(transfer_id,from_account_id,to_account_id,amount_minor,currency,request_hash,status,failure_code)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        c.transferId(),
        c.fromAccount(),
        c.toAccount(),
        amount,
        c.currency(),
        hash,
        status,
        reason);
  }

  public void entry(UUID account, UUID transfer, String direction, long amount, String currency) {
    db.update(
        "INSERT INTO ledger_entries(id,account_id,transfer_id,direction,amount_minor,currency)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        account,
        transfer,
        direction,
        amount,
        currency);
  }

  public PageResponse<Entry> entries(UUID account, int page, int size) {
    PageResponse.validate(page, size);
    long count =
        db.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE account_id=?", Long.class, account);
    var rows =
        db.query(
            "SELECT * FROM ledger_entries WHERE account_id=? ORDER BY created_at DESC,id DESC LIMIT"
                + " ? OFFSET ?",
            (r, n) ->
                new Entry(
                    r.getObject("id", UUID.class),
                    r.getObject("transfer_id", UUID.class),
                    r.getString("direction"),
                    Long.toString(r.getLong("amount_minor")),
                    r.getString("currency"),
                    r.getTimestamp("created_at").toInstant()),
            account,
            size,
            (long) page * size);
    return PageResponse.of(rows, page, size, count);
  }
}
