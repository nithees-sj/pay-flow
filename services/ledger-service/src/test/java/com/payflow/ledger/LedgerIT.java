package com.payflow.ledger;

import static org.assertj.core.api.Assertions.*;

import com.payflow.common.contracts.*;
import com.payflow.common.error.ApiException;
import com.payflow.ledger.transfer.domain.TransferService;
import com.payflow.ledger.wallet.domain.WalletService;
import com.payflow.ledger.wallet.infra.AccountRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest
class LedgerIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("payflow.jwt-secret", () -> "test-jwt-secret-at-least-32-characters-long");
    r.add("payflow.accounts-token", () -> "test-accounts");
    r.add("payflow.payments-token", () -> "test-payments");
    r.add("payflow.demo-funding", () -> true);
    r.add("payflow.topup-limit", () -> Long.MAX_VALUE);
  }

  @Autowired TransferService service;
  @Autowired WalletService wallets;
  @Autowired AccountRepository accounts;
  @Autowired JdbcTemplate db;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;

  @Test
  void happyPathIdempotencyAndConflictingReuse() {
    UUID a = wallet(1000), b = wallet(0), id = UUID.randomUUID();
    var c = command(id, a, b, 100);
    assertThat(service.post(c).status()).isEqualTo("POSTED");
    assertThat(service.post(c)).isEqualTo(service.find(id));
    assertThat(entries(id)).isEqualTo(2);
    assertThat(accounts.get(a).orElseThrow().balance()).isEqualTo(900);
    assertThatThrownBy(() -> service.post(command(id, a, b, 101))).isInstanceOf(ApiException.class);
  }

  @Test
  void insufficientFundsIsDurableEvenAfterFunding() {
    UUID a = wallet(0), b = wallet(0), id = UUID.randomUUID();
    var c = command(id, a, b, 100);
    assertThat(service.post(c).failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(entries(id)).isZero();
    service.topup(a, UUID.randomUUID(), "1000", "INR");
    assertThat(service.post(c).status()).isEqualTo("REJECTED");
    assertThat(entries(id)).isZero();
  }

  @Test
  void fiftyConcurrentRequestsOnlyTenSucceed() throws Exception {
    UUID a = wallet(1000), b = wallet(0);
    var tasks = new ArrayList<Callable<TransferOutcome>>();
    for (int i = 0; i < 50; i++)
      tasks.add(() -> service.post(command(UUID.randomUUID(), a, b, 100)));
    var results = parallel(tasks);
    assertThat(results.stream().filter(r -> r.status().equals("POSTED")).count()).isEqualTo(10);
    assertThat(accounts.get(a).orElseThrow().balance()).isZero();
    invariants();
  }

  @Test
  void concurrentDuplicateCreatesOnePair() throws Exception {
    UUID a = wallet(1000), b = wallet(0);
    var c = command(UUID.randomUUID(), a, b, 100);
    var tasks = new ArrayList<Callable<TransferOutcome>>();
    for (int i = 0; i < 20; i++) tasks.add(() -> service.post(c));
    assertThat(parallel(tasks)).allMatch(r -> r.status().equals("POSTED"));
    assertThat(entries(c.transferId())).isEqualTo(2);
  }

  @Test
  void oppositeDirectionsDoNotDeadlock() throws Exception {
    UUID a = wallet(10000), b = wallet(10000);
    var tasks = new ArrayList<Callable<TransferOutcome>>();
    for (int i = 0; i < 50; i++) {
      boolean forward = i % 2 == 0;
      tasks.add(
          () -> service.post(command(UUID.randomUUID(), forward ? a : b, forward ? b : a, 10)));
    }
    assertThat(parallel(tasks)).allMatch(r -> r.status().equals("POSTED"));
    invariants();
  }

  @Test
  void randomizedTransfersConserveMoney() {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 8; i++) ids.add(wallet(1000));
    var random = new Random(42);
    for (int i = 0; i < 200; i++) {
      int a = random.nextInt(8), b = (a + 1 + random.nextInt(7)) % 8;
      service.post(command(UUID.randomUUID(), ids.get(a), ids.get(b), 1 + random.nextInt(300)));
    }
    invariants();
  }

  @Test
  void rejectsOverflowWithoutEntries() {
    UUID a = wallet(10), b = wallet(0);
    db.update("UPDATE ledger_accounts SET balance_minor=? WHERE id=?", Long.MAX_VALUE, b);
    try {
      UUID id = UUID.randomUUID();
      assertThat(service.post(command(id, a, b, 1)).failureCode()).isEqualTo("AMOUNT_OVERFLOW");
      assertThat(entries(id)).isZero();
    } finally {
      db.update("UPDATE ledger_accounts SET balance_minor=0 WHERE id=?", b);
    }
  }

  @Test
  void databaseRejectsIncompleteJournal() {
    UUID a = wallet(1), b = wallet(0);
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(s ->
                    db.update(
                        "INSERT INTO"
                            + " transfers(transfer_id,from_account_id,to_account_id,amount_minor,currency,request_hash,status)"
                            + " VALUES(?,?,?,1,'INR',?,'POSTED')",
                        UUID.randomUUID(),
                        a,
                        b,
                        "x".repeat(64))))
        .isInstanceOf(RuntimeException.class);
    invariants();
  }

  @Test
  void journalCannotBeChanged() {
    UUID a = wallet(1);
    assertThatThrownBy(
            () -> db.update("UPDATE ledger_entries SET amount_minor=2 WHERE account_id=?", a))
        .isInstanceOf(RuntimeException.class);
  }

  private UUID wallet(long amount) {
    UUID id = UUID.randomUUID();
    wallets.provision(id, UUID.randomUUID(), "INR");
    if (amount > 0) service.topup(id, UUID.randomUUID(), Long.toString(amount), "INR");
    return id;
  }

  private TransferCommand command(UUID id, UUID a, UUID b, long amount) {
    return new TransferCommand(id, a, b, Long.toString(amount), "INR");
  }

  private long entries(UUID id) {
    return db.queryForObject(
        "SELECT count(*) FROM ledger_entries WHERE transfer_id=?", Long.class, id);
  }

  private List<TransferOutcome> parallel(List<Callable<TransferOutcome>> tasks) throws Exception {
    var pool = Executors.newFixedThreadPool(50);
    try {
      var results = new ArrayList<TransferOutcome>();
      for (var f : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) results.add(f.get());
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private void invariants() {
    assertThat(
            db.queryForObject(
                "SELECT coalesce(sum(CASE WHEN direction='CREDIT' THEN amount_minor::numeric ELSE"
                    + " -amount_minor::numeric END),0) FROM ledger_entries",
                BigDecimal.class))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM ledger_accounts WHERE kind='USER' AND balance_minor<0",
                Long.class))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM ledger_accounts a WHERE a.balance_minor::numeric<>(SELECT"
                    + " coalesce(sum(CASE WHEN direction='CREDIT' THEN e.amount_minor::numeric ELSE"
                    + " -e.amount_minor::numeric END),0) FROM ledger_entries e WHERE"
                    + " e.account_id=a.id)",
                Long.class))
        .isZero();
  }
}
