package com.payflow.payments.idempotency.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payments.idempotency.domain.StoredResponse;
import java.time.*;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCache {
  public record Cached(String requestHash, StoredResponse response, Instant expiresAt) {}

  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public IdempotencyCache(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  public Optional<Cached> get(String key) {
    try {
      String v = redis.opsForValue().get(key);
      if (v == null) return Optional.empty();
      var c = json.readValue(v, Cached.class);
      return c.expiresAt().isAfter(Instant.now()) ? Optional.of(c) : Optional.empty();
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  public void put(String key, Cached value) {
    try {
      var ttl = Duration.between(Instant.now(), value.expiresAt());
      if (!ttl.isNegative() && !ttl.isZero())
        redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
    } catch (Exception ignored) {
      /* Durable response remains authoritative. */
    }
  }
}
