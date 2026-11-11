package com.payflow.payments;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.payflow.common.contracts.*;
import com.payflow.common.error.ApiException;
import com.payflow.payments.events.infra.OutboxPublisher;
import com.payflow.payments.idempotency.domain.StoredResponse;
import com.payflow.payments.payment.domain.*;
import com.payflow.payments.payment.infra.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest
class PaymentsIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7").withExposedPorts(6379);

  @Container
  static KafkaContainer kafka =
      new KafkaContainer("apache/kafka:3.9.1").withEnv("KAFKA_HEAP_OPTS", "-Xms128m -Xmx256m");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redisContainer::getHost);
    r.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    r.add("payflow.jwt-secret", () -> "test-jwt-secret-at-least-32-characters-long");
    r.add("payflow.accounts-token", () -> "test-accounts");
    r.add("payflow.payments-token", () -> "test-payments");
    r.add("payflow.demo-funding", () -> true);
    r.add("payflow.risk.minute-limit", () -> 10);
    r.add("payflow.risk.daily-limit", () -> 1000);
    r.add("payflow.reconcile-delay", () -> 3600000);
    r.add("payflow.outbox-delay", () -> 3600000);
  }

  @Autowired com.payflow.payments.idempotency.domain.IdempotencyService idempotency;
  @Autowired PaymentProcessor processor;
  @Autowired PaymentService service;
  @Autowired PaymentRepository repo;
  @Autowired JdbcTemplate db;
  @Autowired StringRedisTemplate redis;
  @Autowired OutboxPublisher publisher;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;
  @MockitoBean PaymentClients clients;
  UUID sender, receiver;
  Map<UUID, TransferOutcome> ledger;

  @BeforeEach
  void setup() {
    sender = UUID.randomUUID();
    receiver = UUID.randomUUID();
    ledger = new ConcurrentHashMap<>();
    when(clients.sender(any()))
        .thenAnswer(i ->
            new WalletResolution(
                i.getArgument(0),
                UUID.nameUUIDFromBytes(i.getArgument(0).toString().getBytes()),
                "INR",
                "ACTIVE"));
    when(clients.recipient(any()))
        .thenAnswer(i ->
            new WalletResolution(
                i.getArgument(0).equals("self@example.test") ? sender : receiver,
                UUID.nameUUIDFromBytes(i.getArgument(0).toString().getBytes()),
                "INR",
                "ACTIVE"));
    when(clients.transfer(any(Payment.class)))
        .thenAnswer(i -> {
          Payment p = i.getArgument(0);
          return ledger.computeIfAbsent(
              p.transferId(), id -> new TransferOutcome(id, "POSTED", null));
        });
  }

  @Test
  void twentyParallelRetriesProduceOnePaymentAndTransfer() throws Exception {
    var pool = Executors.newFixedThreadPool(20);
    String key = UUID.randomUUID().toString();
    try {
      var tasks = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 20; i++)
        tasks.add(() -> {
          try {
            return send(key, "10").status();
          } catch (ApiException e) {
            return e.status();
          }
        });
      for (var f : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) assertThat(f.get()).isIn(201, 409);
      assertThat(
              db.queryForObject(
                  "SELECT count(*) FROM payments WHERE initiated_by=?", Long.class, sender))
          .isEqualTo(1);
      assertThat(ledger).hasSize(1);
      verify(clients, times(1)).transfer(any());
      var first = send(key, "10");
      assertThat(send(key, "10")).isEqualTo(first);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void changedBodyReturns422AndRedisLossDoesNotDuplicate() {
    String key = UUID.randomUUID().toString();
    var original = send(key, "10");
    assertThatThrownBy(() -> send(key, "11"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(422));
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    assertThat(send(key, "10")).isEqualTo(original);
    assertThat(ledger).hasSize(1);
  }

  @Test
  void inFlightDuplicateReturns409() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(clients.transfer(any(Payment.class)))
        .thenAnswer(i -> {
          entered.countDown();
          if (!release.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("Test release timeout");
          Payment p = i.getArgument(0);
          return new TransferOutcome(p.transferId(), "POSTED", null);
        });
    var pool = Executors.newSingleThreadExecutor();
    String key = UUID.randomUUID().toString();
    try {
      var first = pool.submit(() -> send(key, "10"));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> send(key, "10"))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(409));
      release.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(201);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void timeoutReconcilesSameTransferAndReplaysOriginal202() {
    var first = new AtomicBoolean(true);
    when(clients.transfer(any(Payment.class)))
        .thenAnswer(i -> {
          Payment p = i.getArgument(0);
          var result =
              ledger.computeIfAbsent(p.transferId(), id -> new TransferOutcome(id, "POSTED", null));
          if (first.getAndSet(false))
            throw new ResourceAccessException("Simulated lost response after commit");
          return result;
        });
    String key = UUID.randomUUID().toString();
    var initial = send(key, "10");
    assertThat(initial.status()).isEqualTo(202);
    UUID id = id(initial);
    assertThat(repo.get(id).status()).isEqualTo(PaymentStatus.CREATED);
    db.update("UPDATE payments SET next_reconcile_at=now(),lease_until=NULL WHERE id=?", id);
    service.reconcile();
    assertThat(repo.get(id).status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(ledger).hasSize(1);
    assertThat(send(key, "10")).isEqualTo(initial);
    assertThat(eventCount(id)).isEqualTo(1);
  }

  @Test
  void checksStaticAndDailyRisk() {
    assertThat(send(UUID.randomUUID().toString(), "0").body().get("reasonCode").asText())
        .isEqualTo("INVALID_AMOUNT");
    assertThat(
            service
                .submit(
                    sender,
                    "PAYMENT",
                    UUID.randomUUID().toString(),
                    "self@example.test",
                    "1",
                    "INR",
                    "",
                    UUID.randomUUID().toString())
                .body()
                .get("reasonCode")
                .asText())
        .isEqualTo("SELF_TRANSFER");
    assertThat(send(UUID.randomUUID().toString(), "1001").body().get("reasonCode").asText())
        .isEqualTo("DAILY_AMOUNT_LIMIT_EXCEEDED");
    assertThat(ledger).isEmpty();
  }

  @Test
  void velocitySurvivesCounterLossAndDoesNotCountRetries() {
    String key = UUID.randomUUID().toString();
    send(key, "1");
    for (int i = 0; i < 20; i++) send(key, "1");
    for (int i = 0; i < 9; i++)
      assertThat(send(UUID.randomUUID().toString(), "1").body().get("status").asText())
          .isEqualTo("COMPLETED");
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    assertThat(send(UUID.randomUUID().toString(), "1").body().get("reasonCode").asText())
        .isEqualTo("VELOCITY_LIMIT_EXCEEDED");
  }

  @Test
  void insufficientFundsIsFailedAndOnlyInitiatorSeesIt() {
    when(clients.transfer(any(Payment.class)))
        .thenAnswer(i ->
            new TransferOutcome(
                ((Payment) i.getArgument(0)).transferId(), "REJECTED", "INSUFFICIENT_FUNDS"));
    UUID id = id(send(UUID.randomUUID().toString(), "10"));
    assertThat(repo.visible(id, sender).status()).isEqualTo("FAILED");
    assertThatThrownBy(() -> repo.visible(id, receiver)).isInstanceOf(ApiException.class);
  }

  @Test
  void completedPaymentVisibleToBothParticipantsButNotOutsider() {
    UUID id = id(send(UUID.randomUUID().toString(), "10"));
    assertThat(repo.visible(id, receiver).direction()).isEqualTo("INCOMING");
    assertThat(repo.visible(id, sender).direction()).isEqualTo("OUTGOING");
    assertThatThrownBy(() -> repo.visible(id, UUID.randomUUID()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(404));
  }

  @Test
  void expiredKeyCreatesNewOperation() {
    String key = UUID.randomUUID().toString();
    UUID first = id(send(key, "10"));
    db.update(
        "UPDATE idempotency_records SET expires_at=now()-interval '1 second' WHERE payment_id=?",
        first);
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    assertThat(id(send(key, "10"))).isNotEqualTo(first);
    assertThat(ledger).hasSize(2);
  }

  @Test
  void rolledBackTransitionHasNoEvent() {
    when(clients.transfer(any(Payment.class)))
        .thenThrow(new ResourceAccessException("Unavailable"));
    UUID id = id(send(UUID.randomUUID().toString(), "10"));
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(s -> {
                  repo.finish(new PaymentRepository.Claim(id, 1), PaymentStatus.COMPLETED, null);
                  throw new IllegalStateException("rollback");
                }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(repo.get(id).status()).isEqualTo(PaymentStatus.CREATED);
    assertThat(eventCount(id)).isZero();
    db.update("UPDATE payments SET next_reconcile_at=now()+interval '1 day' WHERE id=?", id);
  }

  @Test
  void committedOutboxPublishesToKafka() {
    UUID id = id(send(UUID.randomUUID().toString(), "10"));
    var props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (var consumer = new KafkaConsumer<String, String>(props)) {
      consumer.subscribe(List.of("payments.events"));
      for (int i = 0;
          i < 10
              && db.queryForObject(
                      "SELECT count(*) FROM outbox_events WHERE payment_id=? AND published_at IS"
                          + " NOT NULL",
                      Long.class,
                      id)
                  == 0;
          i++) publisher.publish();
      boolean found = false;
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (System.nanoTime() < end && !found)
        for (var record : consumer.poll(Duration.ofMillis(500)))
          if (record.key().equals(id.toString())) {
            assertThat(record.value()).contains("payment.completed", "schemaVersion");
            found = true;
          }
      assertThat(found).isTrue();
    }
  }

  @Test
  void terminalTransitionCannotBeOverwritten() {
    UUID id = id(send(UUID.randomUUID().toString(), "10"));
    assertThat(
            repo.finish(
                new PaymentRepository.Claim(id, 1), PaymentStatus.FAILED, "INSUFFICIENT_FUNDS"))
        .isFalse();
    assertThat(repo.get(id).status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThatThrownBy(() -> PaymentStatus.COMPLETED.requireTransitionTo(PaymentStatus.FAILED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rolledBackResponseNeverAppearsInRedis() {
    String key = UUID.randomUUID().toString();
    var intent =
        new com.payflow.payments.idempotency.domain.IdempotencyService.Intent(
            "receiver@example.test", "10", "INR", "test");
    UUID id =
        idempotency
            .reserve(sender, "PAYMENT", key, intent, UUID.randomUUID().toString())
            .paymentId();
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(t -> {
                  idempotency.save(id, 202, repo.view(repo.get(id), sender));
                  throw new IllegalStateException("rollback response");
                }))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> idempotency.reserve(sender, "PAYMENT", key, intent, UUID.randomUUID().toString()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(409));
    db.update("UPDATE payments SET next_reconcile_at=now()+interval '1 day' WHERE id=?", id);
  }

  @Test
  void recoversResponseWhenProcessDiesAfterTerminalCommit() {
    String key = UUID.randomUUID().toString();
    var intent =
        new com.payflow.payments.idempotency.domain.IdempotencyService.Intent(
            "receiver@example.test", "10", "INR", "test");
    UUID id =
        idempotency
            .reserve(sender, "PAYMENT", key, intent, UUID.randomUUID().toString())
            .paymentId();
    processor.process(new PaymentRepository.Claim(id, 1));
    assertThat(repo.get(id).status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(
            db.queryForObject(
                "SELECT response_status FROM idempotency_records WHERE payment_id=?",
                Integer.class,
                id))
        .isNull();
    var recovered = send(key, "10");
    assertThat(recovered.status()).isEqualTo(201);
    assertThat(id(recovered)).isEqualTo(id);
    assertThat(send(key, "10")).isEqualTo(recovered);
    verify(clients, times(1)).transfer(any());
  }

  private StoredResponse send(String key, String amount) {
    return service.submit(
        sender,
        "PAYMENT",
        key,
        "receiver@example.test",
        amount,
        "INR",
        "test",
        UUID.randomUUID().toString());
  }

  private UUID id(StoredResponse r) {
    return UUID.fromString(r.body().get("id").asText());
  }

  private long eventCount(UUID id) {
    return db.queryForObject(
        "SELECT count(*) FROM outbox_events WHERE payment_id=?", Long.class, id);
  }
}
