package com.payflow.ledger.wallet.domain;

import com.payflow.common.error.ApiException;
import com.payflow.ledger.wallet.infra.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
  private final AccountRepository accounts;

  public WalletService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional
  public void provision(UUID id, UUID user, String currency) {
    if (!"INR".equals(currency)) throw new IllegalArgumentException("INR only");
    accounts.create(id, user, currency);
    var account =
        accounts
            .get(id)
            .orElseThrow(
                () -> new ApiException(409, "ACCOUNT_ID_REUSED", "User already has a wallet"));
    if (!user.equals(account.owner()) || !currency.equals(account.currency()))
      throw new ApiException(409, "ACCOUNT_ID_REUSED", "Account ID already used");
  }

  public AccountRepository.Account owned(UUID user) {
    return accounts
        .owned(user)
        .orElseThrow(() -> new ApiException(409, "WALLET_NOT_READY", "Wallet not ready"));
  }
}
