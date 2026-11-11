package com.payflow.payments.events.infra;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository {
  private final JdbcTemplate db;

  public OutboxRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Event(UUID eventId, String key, String payload, UUID lease) {}

  @Transactional
  public List<Event> claim() {
    UUID lease = UUID.randomUUID();
    return db.query(
        "UPDATE outbox_events SET lease_owner=?,lease_until=now()+interval '30"
            + " seconds',attempts=attempts+1 WHERE event_id IN(SELECT event_id FROM outbox_events"
            + " WHERE published_at IS NULL AND next_attempt_at<=now() AND (lease_until IS NULL OR"
            + " lease_until<now()) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 10) RETURNING"
            + " event_id,partition_key,payload",
        (r, n) -> new Event(r.getObject(1, UUID.class), r.getString(2), r.getString(3), lease),
        lease);
  }

  public void published(Event e) {
    db.update(
        "UPDATE outbox_events SET published_at=now(),lease_until=NULL,last_error_code=NULL WHERE"
            + " event_id=? AND lease_owner=? AND published_at IS NULL",
        e.eventId(),
        e.lease());
  }

  public void retry(Event e) {
    db.update(
        "UPDATE outbox_events SET"
            + " lease_until=NULL,next_attempt_at=now()+make_interval(secs=>least(60,power(2,least(attempts,5))::int)),last_error_code='KAFKA_UNAVAILABLE'"
            + " WHERE event_id=? AND lease_owner=? AND published_at IS NULL",
        e.eventId(),
        e.lease());
  }

  public long backlog() {
    return db.queryForObject(
        "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
  }
}
