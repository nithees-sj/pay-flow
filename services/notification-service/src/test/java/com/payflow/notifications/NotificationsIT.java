package com.payflow.notifications;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.contracts.PaymentEvent;
import com.payflow.common.error.ApiException;
import com.payflow.notifications.notification.domain.NotificationService;
import com.payflow.notifications.notification.infra.NotificationRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest
class NotificationsIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer("apache/kafka:3.9.1").withEnv("KAFKA_HEAP_OPTS", "-Xms128m -Xmx256m");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    r.add("payflow.jwt-secret", () -> "test-jwt-secret-at-least-32-characters-long");
    r.add("payflow.accounts-token", () -> "test-accounts");
    r.add("payflow.payments-token", () -> "test-payments");
  }

  @Autowired NotificationService service;
  @Autowired NotificationRepository repo;
  @Autowired JdbcTemplate db;
  @Autowired KafkaTemplate<String, String> producer;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;

  @Test
  void kafkaDeliveryCreatesBothNotificationsAndDeduplicates() throws Exception {
    var e = event("COMPLETED", null, "PAYMENT");
    String body = json.writeValueAsString(e);
    producer
        .send("payments.events", e.data().paymentId().toString(), body)
        .get(10, TimeUnit.SECONDS);
    producer
        .send("payments.events", e.data().paymentId().toString(), body)
        .get(10, TimeUnit.SECONDS);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(count(e.eventId())).isEqualTo(2));
    assertThat(service.accept(e)).isFalse();
    assertThat(repo.inbox(e.data().initiatedBy(), 0, 20, false).unreadCount()).isEqualTo(1);
    assertThat(repo.inbox(e.data().recipientUserId(), 0, 20, false).unreadCount()).isEqualTo(1);
  }

  @Test
  void failuresNotifyInitiatorAndTopupsNotifyOnce() {
    var failed = event("FAILED", "INSUFFICIENT_FUNDS", "PAYMENT");
    service.accept(failed);
    assertThat(count(failed.eventId())).isEqualTo(1);
    var topup = event("COMPLETED", null, "TEST_TOPUP");
    service.accept(topup);
    assertThat(count(topup.eventId())).isEqualTo(1);
  }

  @Test
  void ownershipAndRepeatedMarkRead() {
    var e = event("COMPLETED", null, "PAYMENT");
    service.accept(e);
    var n = repo.inbox(e.data().initiatedBy(), 0, 20, false).items().get(0);
    assertThatThrownBy(() -> repo.markRead(n.id(), UUID.randomUUID()))
        .isInstanceOf(ApiException.class);
    var first = repo.markRead(n.id(), e.data().initiatedBy());
    assertThat(repo.markRead(n.id(), e.data().initiatedBy()).readAt()).isEqualTo(first.readAt());
    assertThat(repo.inbox(e.data().initiatedBy(), 0, 20, true).items()).isEmpty();
  }

  @Test
  void receiptAndNotificationsRollbackTogether() {
    var e = event("COMPLETED", null, "PAYMENT");
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(s -> {
                  service.accept(e);
                  throw new IllegalStateException("rollback");
                }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count(e.eventId())).isZero();
    assertThat(service.accept(e)).isTrue();
    assertThat(count(e.eventId())).isEqualTo(2);
  }

  @Test
  void poisonMessageReachesDlt() throws Exception {
    String poison = "{\"schemaVersion\":999,\"eventId\":\"" + UUID.randomUUID() + "\"}";
    var props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (var consumer = new KafkaConsumer<String, String>(props)) {
      consumer.subscribe(List.of("payments.events.DLT"));
      producer.send("payments.events", "poison", poison).get(10, TimeUnit.SECONDS);
      boolean found = false;
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
      while (System.nanoTime() < end && !found)
        for (var record : consumer.poll(Duration.ofMillis(500)))
          if (record.value().equals(poison)) {
            found = true;
            assertThat(record.headers().toArray()).isNotEmpty();
          }
      assertThat(found).isTrue();
    }
  }

  private long count(UUID event) {
    return db.queryForObject(
        "SELECT count(*) FROM notifications WHERE event_id=?", Long.class, event);
  }

  private PaymentEvent event(String status, String reason, String kind) {
    UUID sender = UUID.randomUUID(),
        receiver = kind.equals("TEST_TOPUP") ? sender : UUID.randomUUID();
    return new PaymentEvent(
        UUID.randomUUID(),
        "payment." + status.toLowerCase(Locale.ROOT),
        1,
        Instant.now(),
        UUID.randomUUID().toString(),
        new PaymentEvent.Data(
            UUID.randomUUID(),
            UUID.randomUUID(),
            kind,
            sender,
            kind.equals("TEST_TOPUP") ? null : sender,
            receiver,
            "100",
            "INR",
            status,
            reason));
  }
}
