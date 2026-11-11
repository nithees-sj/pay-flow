package com.payflow.payments.payment.domain;

import com.payflow.common.contracts.WalletResolution;
import com.payflow.payments.payment.infra.*;
import com.payflow.payments.risk.domain.RiskService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class PaymentProcessor {
  private final PaymentRepository repo;
  private final PaymentClients clients;
  private final RiskService risk;
  private final MeterRegistry metrics;

  public PaymentProcessor(
      PaymentRepository repo, PaymentClients clients, RiskService risk, MeterRegistry metrics) {
    this.repo = repo;
    this.clients = clients;
    this.risk = risk;
    this.metrics = metrics;
  }

  /**
   * Drives one claimed payment; uncertain infrastructure outcomes are retried with the same
   * transfer ID.
   */
  public void process(PaymentRepository.Claim claim) {
    var p = repo.get(claim.id());
    if (p.status() != PaymentStatus.CREATED || p.version() != claim.version()) return;
    try {
      if (p.amount() <= 0) {
        finish(claim, PaymentStatus.DECLINED, "INVALID_AMOUNT");
        return;
      }
      if (p.toAccount() == null) {
        WalletResolution own = clients.sender(p.initiatedBy()), recipient;
        try {
          recipient = p.kind().equals("TEST_TOPUP") ? own : clients.recipient(p.recipientEmail());
        } catch (HttpClientErrorException.NotFound e) {
          finish(claim, PaymentStatus.FAILED, "RECIPIENT_NOT_FOUND");
          return;
        }
        if (!repo.resolve(
            claim,
            p.kind().equals("PAYMENT") ? own.userId() : null,
            recipient.userId(),
            p.kind().equals("PAYMENT")
                ? own.accountId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001"),
            recipient.accountId())) return;
        p = repo.get(p.id());
      }
      String decision = risk.evaluate(p);
      if (!"ALLOWED".equals(decision)) {
        finish(claim, PaymentStatus.DECLINED, decision);
        return;
      }
      var result = clients.transfer(p);
      finish(
          claim,
          result.status().equals("POSTED") ? PaymentStatus.COMPLETED : PaymentStatus.FAILED,
          result.failureCode());
    } catch (RestClientException | DataAccessException e) {
      repo.defer(claim);
      metrics.counter("payflow.reconciliation.deferrals").increment();
    }
  }

  private void finish(PaymentRepository.Claim claim, PaymentStatus state, String reason) {
    if (repo.finish(claim, state, reason))
      metrics.counter("payflow.payments", "status", state.name()).increment();
  }
}
