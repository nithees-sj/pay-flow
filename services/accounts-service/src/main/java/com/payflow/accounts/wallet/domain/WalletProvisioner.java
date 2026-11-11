package com.payflow.accounts.wallet.domain;

import com.payflow.accounts.wallet.infra.WalletRepository;
import com.payflow.common.http.InternalClients;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WalletProvisioner {
  private final WalletRepository wallets;
  private final RestClient ledger;

  public WalletProvisioner(
      WalletRepository wallets,
      @Value("${payflow.ledger-url}") String url,
      @Value("${payflow.accounts-token}") String token) {
    this.wallets = wallets;
    ledger = InternalClients.create(url, "accounts", token);
  }

  public void provision(WalletRepository.Wallet wallet) {
    if (wallet.status().equals("ACTIVE")) return;
    try {
      ledger
          .put()
          .uri("/internal/accounts/{id}", wallet.id())
          .body(Map.of("ownerUserId", wallet.userId(), "currency", wallet.currency()))
          .retrieve()
          .toBodilessEntity();
      wallets.active(wallet.id());
    } catch (org.springframework.web.client.RestClientException e) {
      wallets.retry(wallet.id());
    }
  }

  @Scheduled(fixedDelayString = "${payflow.provision-delay:5000}")
  public void recover() {
    for (var w : wallets.pending()) provision(w);
  }
}
