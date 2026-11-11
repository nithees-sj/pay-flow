package com.payflow.ledger.transfer.api;

import com.payflow.common.api.PageResponse;
import com.payflow.common.contracts.*;
import com.payflow.ledger.transfer.domain.TransferService;
import com.payflow.ledger.transfer.infra.TransferRepository;
import com.payflow.ledger.wallet.domain.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferController {
  private final TransferService service;
  private final TransferRepository transfers;
  private final WalletService wallets;

  public TransferController(
      TransferService service, TransferRepository transfers, WalletService wallets) {
    this.service = service;
    this.transfers = transfers;
    this.wallets = wallets;
  }

  public record Topup(
      @NotNull UUID transferId, @NotBlank String amount, @NotBlank String currency) {}

  @PostMapping("/internal/transfers")
  @Operation(summary = "Post an idempotent double-entry transfer")
  public TransferOutcome post(@Valid @RequestBody TransferCommand c) {
    return service.post(c);
  }

  @GetMapping("/internal/transfers/{id}")
  @Operation(summary = "Read the durable ledger outcome")
  public TransferOutcome find(@PathVariable UUID id) {
    return service.find(id);
  }

  @PostMapping("/internal/accounts/{id}/topup")
  @Operation(summary = "Add demo funds from the system account")
  public TransferOutcome topup(@PathVariable UUID id, @Valid @RequestBody Topup r) {
    return service.topup(id, r.transferId(), r.amount(), r.currency());
  }

  @GetMapping("/api/wallet/entries")
  @Operation(summary = "Read own paginated journal entries")
  public PageResponse<TransferRepository.Entry> entries(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return transfers.entries(wallets.owned(UUID.fromString(jwt.getSubject())).id(), page, size);
  }
}
