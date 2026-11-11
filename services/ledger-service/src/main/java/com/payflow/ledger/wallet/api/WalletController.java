package com.payflow.ledger.wallet.api;

import com.payflow.ledger.wallet.domain.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class WalletController {
  private final WalletService wallets;

  public WalletController(WalletService wallets) {
    this.wallets = wallets;
  }

  public record CreateAccount(@NotNull UUID ownerUserId, @NotBlank String currency) {}

  @PutMapping("/internal/accounts/{id}")
  @Operation(summary = "Idempotently provision a financial account")
  public Map<String, Object> create(@PathVariable UUID id, @Valid @RequestBody CreateAccount r) {
    wallets.provision(id, r.ownerUserId(), r.currency());
    return Map.of("accountId", id, "currency", r.currency());
  }

  @GetMapping("/api/wallet")
  @Operation(summary = "Read own authoritative balance")
  public Map<String, Object> balance(@AuthenticationPrincipal Jwt jwt) {
    var a = wallets.owned(UUID.fromString(jwt.getSubject()));
    return Map.of(
        "accountId",
        a.id(),
        "currency",
        a.currency(),
        "balance",
        Long.toString(a.balance()),
        "updatedAt",
        a.updatedAt());
  }
}
