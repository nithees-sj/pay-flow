package com.payflow.payments.payment.infra;

import com.payflow.common.contracts.*;
import com.payflow.common.http.InternalClients;
import com.payflow.payments.payment.domain.Payment;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class PaymentClients {
  private final RestClient accounts;
  private final RestClient ledger;

  public PaymentClients(
      @Value("${payflow.accounts-url}") String accountsUrl,
      @Value("${payflow.ledger-url}") String ledgerUrl,
      @Value("${payflow.payments-token}") String token) {
    accounts = InternalClients.create(accountsUrl, "payments", token);
    ledger = InternalClients.create(ledgerUrl, "payments", token);
  }

  public WalletResolution sender(UUID user) {
    return accounts
        .get()
        .uri("/internal/users/{id}/wallet", user)
        .retrieve()
        .body(WalletResolution.class);
  }

  public WalletResolution recipient(String email) {
    return accounts
        .get()
        .uri(b -> b.path("/internal/wallets/resolve").queryParam("email", email).build())
        .retrieve()
        .body(WalletResolution.class);
  }

  public TransferOutcome transfer(Payment p) {
    TransferOutcome result;
    try {
      result =
          ledger
              .get()
              .uri("/internal/transfers/{id}", p.transferId())
              .header("X-Trace-Id", p.traceId())
              .retrieve()
              .body(TransferOutcome.class);
    } catch (HttpClientErrorException.NotFound e) {
      if (p.kind().equals("TEST_TOPUP"))
        result =
            ledger
                .post()
                .uri("/internal/accounts/{id}/topup", p.toAccount())
                .header("X-Trace-Id", p.traceId())
                .body(
                    Map.of(
                        "transferId",
                        p.transferId(),
                        "amount",
                        Long.toString(p.amount()),
                        "currency",
                        p.currency()))
                .retrieve()
                .body(TransferOutcome.class);
      else
        result =
            ledger
                .post()
                .uri("/internal/transfers")
                .header("X-Trace-Id", p.traceId())
                .body(
                    new TransferCommand(
                        p.transferId(),
                        p.fromAccount(),
                        p.toAccount(),
                        Long.toString(p.amount()),
                        p.currency()))
                .retrieve()
                .body(TransferOutcome.class);
    }
    if (result == null
        || !p.transferId().equals(result.transferId())
        || !Set.of("POSTED", "REJECTED").contains(result.status())
        || (result.status().equals("POSTED") && result.failureCode() != null)
        || (result.status().equals("REJECTED") && result.failureCode() == null))
      throw new RestClientException("Invalid ledger response");
    return result;
  }
}
