package com.payflow.payments.idempotency.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ApiException;
import com.payflow.common.util.Hashes;
import com.payflow.payments.idempotency.infra.IdempotencyCache;
import com.payflow.payments.payment.infra.PaymentRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final IdempotencyCache cache;
  private final PaymentRepository payments;

  public IdempotencyService(
      JdbcTemplate db,
      ObjectMapper json,
      org.springframework.transaction.PlatformTransactionManager manager,
      IdempotencyCache cache,
      PaymentRepository payments) {
    this.db = db;
    this.json = json;
    tx = new TransactionTemplate(manager);
    this.cache = cache;
    this.payments = payments;
  }

  public record Intent(String recipientEmail, String amount, String currency, String note) {}

  public record Reservation(UUID paymentId, StoredResponse replay) {}

  private record Row(
      UUID paymentId, String requestHash, Integer status, String body, Instant expiresAt) {}

  /**
   * Atomically reserves one payment per user/operation/key; Redis is only a fast response lookup.
   */
  public Reservation reserve(UUID user, String kind, String key, Intent intent, String traceId) {
    if (key == null || key.isBlank())
      throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
    if (key.length() > 128 || !key.matches("[ -~]+"))
      throw new IllegalArgumentException("Invalid Idempotency-Key");
    String keyHash = Hashes.sha256(key),
        requestHash = serializeHash(intent),
        cacheKey = "idem:" + user + ":" + kind + ":" + keyHash;
    var cached = cache.get(cacheKey);
    if (cached.isPresent()) {
      compare(cached.get().requestHash(), requestHash);
      return new Reservation(null, cached.get().response());
    }
    return tx.execute(t -> {
      db.queryForObject(
          "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
          Object.class,
          user + ":" + kind + ":" + keyHash);
      var rows =
          db.query(
              "SELECT * FROM idempotency_records WHERE user_id=? AND operation=? AND key_hash=?",
              (r, n) ->
                  new Row(
                      r.getObject("payment_id", UUID.class),
                      r.getString("request_hash"),
                      (Integer) r.getObject("response_status"),
                      r.getString("response_body"),
                      r.getTimestamp("expires_at").toInstant()),
              user,
              kind,
              keyHash);
      if (!rows.isEmpty() && rows.get(0).expiresAt().isAfter(Instant.now())) {
        var row = rows.get(0);
        compare(row.requestHash(), requestHash);
        if (row.status() == null) {
          var payment = payments.get(row.paymentId());
          if (payment.status() != com.payflow.payments.payment.domain.PaymentStatus.CREATED) {
            return new Reservation(
                row.paymentId(), save(row.paymentId(), 201, payments.view(payment, user)));
          }
          throw new ApiException(
              409, "REQUEST_IN_PROGRESS", "This request is still being processed");
        }
        var response =
            new StoredResponse(row.status(), read(row.body()), "/api/payments/" + row.paymentId());
        cacheAfterCommit(
            cacheKey, new IdempotencyCache.Cached(requestHash, response, row.expiresAt()));
        return new Reservation(row.paymentId(), response);
      }
      db.update(
          "DELETE FROM idempotency_records WHERE user_id=? AND operation=? AND key_hash=?",
          user,
          kind,
          keyHash);
      UUID payment = UUID.randomUUID(), transfer = UUID.randomUUID();
      db.update(
          "INSERT INTO"
              + " payments(id,transfer_id,kind,initiated_by,sender_user_id,recipient_user_id,recipient_email,amount_minor,currency,note,status,lease_until,trace_id)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,'CREATED',now()+interval '30 seconds',?)",
          payment,
          transfer,
          kind,
          user,
          kind.equals("PAYMENT") ? user : null,
          kind.equals("TEST_TOPUP") ? user : null,
          intent.recipientEmail(),
          Long.parseLong(intent.amount()),
          intent.currency(),
          intent.note(),
          traceId);
      db.update(
          "INSERT INTO payment_transitions(id,payment_id,to_status) VALUES(?,?,'CREATED')",
          UUID.randomUUID(),
          payment);
      db.update(
          "INSERT INTO idempotency_records(user_id,operation,key_hash,request_hash,payment_id)"
              + " VALUES(?,?,?,?,?)",
          user,
          kind,
          keyHash,
          requestHash,
          payment);
      return new Reservation(payment, null);
    });
  }

  public StoredResponse save(UUID paymentId, int status, Object body) {
    String encoded;
    try {
      encoded = json.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return tx.execute(t -> {
      db.update(
          "UPDATE idempotency_records SET"
              + " response_status=?,response_body=?::jsonb,response_headers=jsonb_build_object('Location',?)"
              + " WHERE payment_id=? AND response_status IS NULL",
          status,
          encoded,
          "/api/payments/" + paymentId,
          paymentId);
      var rows =
          db.query(
              "SELECT * FROM idempotency_records WHERE payment_id=?",
              (r, n) -> {
                var response =
                    new StoredResponse(
                        r.getInt("response_status"),
                        read(r.getString("response_body")),
                        "/api/payments/" + paymentId);
                cacheAfterCommit(
                    "idem:"
                        + r.getObject("user_id")
                        + ":"
                        + r.getString("operation")
                        + ":"
                        + r.getString("key_hash"),
                    new IdempotencyCache.Cached(
                        r.getString("request_hash"),
                        response,
                        r.getTimestamp("expires_at").toInstant()));
                return response;
              },
              paymentId);
      return rows.isEmpty()
          ? new StoredResponse(status, read(encoded), "/api/payments/" + paymentId)
          : rows.get(0);
    });
  }

  private void cacheAfterCommit(String key, IdempotencyCache.Cached value) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.put(key, value);
          }
        });
  }

  private String serializeHash(Object value) {
    try {
      return Hashes.sha256(json.writeValueAsString(value));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private com.fasterxml.jackson.databind.JsonNode read(String value) {
    try {
      return json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void compare(String stored, String actual) {
    if (!stored.equals(actual))
      throw new ApiException(
          422, "IDEMPOTENCY_KEY_REUSED", "Key already used with a different request");
  }
}
