package com.payflow.ledger.transfer.domain;

import com.payflow.common.contracts.*;
import com.payflow.common.error.ApiException;
import com.payflow.common.money.Money;
import com.payflow.common.util.Hashes;
import com.payflow.ledger.transfer.infra.TransferRepository;
import com.payflow.ledger.wallet.infra.AccountRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
  public static final UUID FUNDING = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final boolean demo;
  private final long topupLimit;

  public TransferService(
      AccountRepository accounts,
      TransferRepository transfers,
      @Value("${payflow.demo-funding}") boolean demo,
      @Value("${payflow.topup-limit}") long topupLimit) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.demo = demo;
    this.topupLimit = topupLimit;
  }

  /** Posts exactly one balanced pair or persists a durable rejection under ordered row locks. */
  @Transactional
  public TransferOutcome post(TransferCommand c) {
    return postLocked(c, false);
  }

  /** Moves simulated funding to a user wallet using the same idempotent transfer engine. */
  @Transactional
  public TransferOutcome topup(UUID account, UUID transfer, String amount, String currency) {
    if (!demo) throw new ApiException(403, "TEST_FUNDING_DISABLED", "Test funding is disabled");
    if (Money.parse(amount) > topupLimit)
      throw new ApiException(422, "TOPUP_LIMIT_EXCEEDED", "Test funding limit exceeded");
    return postLocked(new TransferCommand(transfer, FUNDING, account, amount, currency), true);
  }

  public TransferOutcome find(UUID id) {
    return transfers
        .find(id)
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Transfer not found"))
        .outcome();
  }

  private TransferOutcome postLocked(TransferCommand c, boolean funding) {
    long amount = Money.parse(c.amount());
    if (amount <= 0 || !"INR".equals(c.currency()) || c.fromAccount().equals(c.toAccount()))
      throw new IllegalArgumentException("Invalid transfer");
    String hash =
        Hashes.sha256(c.fromAccount() + "|" + c.toAccount() + "|" + amount + "|" + c.currency());
    transfers.lockIdentity(c.transferId());
    var existing = transfers.find(c.transferId());
    if (existing.isPresent()) {
      if (!existing.get().requestHash().equals(hash))
        throw new ApiException(
            409, "TRANSFER_ID_REUSED", "Transfer ID already used with different parameters");
      return existing.get().outcome();
    }
    var locked = accounts.lock(c.fromAccount(), c.toAccount());
    var from =
        locked.stream()
            .filter(a -> a.id().equals(c.fromAccount()))
            .findFirst()
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Source account not found"));
    var to =
        locked.stream()
            .filter(a -> a.id().equals(c.toAccount()))
            .findFirst()
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Destination account not found"));
    if (!from.currency().equals(c.currency())
        || !to.currency().equals(c.currency())
        || !to.kind().equals("USER")
        || (!funding && !from.kind().equals("USER")))
      throw new IllegalArgumentException("Invalid account role/currency");
    String failure = null;
    long debit = 0, credit = 0;
    if (from.kind().equals("USER") && from.balance() < amount) failure = "INSUFFICIENT_FUNDS";
    else {
      try {
        var movement = new Money(amount, c.currency());
        debit = new Money(from.balance(), from.currency()).subtract(movement).minor();
        credit = new Money(to.balance(), to.currency()).add(movement).minor();
      } catch (ArithmeticException e) {
        failure = "AMOUNT_OVERFLOW";
      }
    }
    if (failure != null) {
      transfers.outcome(c, amount, hash, "REJECTED", failure);
      return new TransferOutcome(c.transferId(), "REJECTED", failure);
    }
    transfers.outcome(c, amount, hash, "POSTED", null);
    transfers.entry(from.id(), c.transferId(), "DEBIT", amount, c.currency());
    transfers.entry(to.id(), c.transferId(), "CREDIT", amount, c.currency());
    accounts.balance(from.id(), debit);
    accounts.balance(to.id(), credit);
    return new TransferOutcome(c.transferId(), "POSTED", null);
  }
}
