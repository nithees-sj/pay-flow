package com.payflow.payments.payment.domain;

import com.payflow.common.error.ApiException;
import com.payflow.common.money.Money;
import com.payflow.payments.idempotency.domain.*;
import com.payflow.payments.payment.infra.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final IdempotencyService idempotency;
  private final PaymentRepository repo;
  private final PaymentProcessor processor;
  private final boolean demo;
  private final long topupLimit;
  private final Timer latency;

  public PaymentService(
      IdempotencyService idempotency,
      PaymentRepository repo,
      PaymentProcessor processor,
      @Value("${payflow.demo-funding}") boolean demo,
      @Value("${payflow.topup-limit}") long topupLimit,
      MeterRegistry metrics) {
    this.idempotency = idempotency;
    this.repo = repo;
    this.processor = processor;
    this.demo = demo;
    this.topupLimit = topupLimit;
    latency = metrics.timer("payflow.payment.request.latency");
  }

  /**
   * Reserves one attempt and returns its immutable initial response; GET reports later
   * reconciliation.
   */
  public StoredResponse submit(
      UUID user,
      String kind,
      String key,
      String recipient,
      String amount,
      String currency,
      String note,
      String trace) {
    if (!"INR".equals(currency))
      throw new ApiException(422, "UNSUPPORTED_CURRENCY", "Only INR is supported");
    long minor = Money.parse(amount);
    if (kind.equals("TEST_TOPUP")) {
      if (!demo) throw new ApiException(403, "TEST_FUNDING_DISABLED", "Test funding is disabled");
      if (minor > topupLimit)
        throw new ApiException(422, "TOPUP_LIMIT_EXCEEDED", "Test funding limit exceeded");
    }
    var intent =
        new IdempotencyService.Intent(
            recipient == null ? null : recipient.strip().toLowerCase(Locale.ROOT),
            Long.toString(minor),
            currency,
            note == null ? "" : note);
    return latency.record(() -> {
      var reservation = idempotency.reserve(user, kind, key, intent, trace);
      if (reservation.replay() != null) return reservation.replay();
      processor.process(new PaymentRepository.Claim(reservation.paymentId(), 1));
      var p = repo.get(reservation.paymentId());
      return idempotency.save(
          p.id(), p.status() == PaymentStatus.CREATED ? 202 : 201, repo.view(p, user));
    });
  }

  @Scheduled(fixedDelayString = "${payflow.reconcile-delay:2000}")
  public void reconcile() {
    for (var claim : repo.claimDue()) {
      try {
        var p = repo.get(claim.id());
        idempotency.save(p.id(), 202, repo.view(p, p.initiatedBy()));
        processor.process(claim);
      } catch (RuntimeException e) {
        repo.defer(claim);
      }
    }
  }
}
