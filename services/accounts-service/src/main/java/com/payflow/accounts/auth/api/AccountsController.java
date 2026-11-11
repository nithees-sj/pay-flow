package com.payflow.accounts.auth.api;

import com.payflow.accounts.auth.domain.AccountsService;
import com.payflow.accounts.wallet.infra.WalletRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountsController {
  private final AccountsService service;

  public AccountsController(AccountsService service) {
    this.service = service;
  }

  public record Register(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record WalletResolution(UUID userId, UUID accountId, String currency, String status) {}

  @PostMapping("/api/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register and provision an INR wallet")
  public AccountsService.Profile register(@Valid @RequestBody Register r) {
    return service.register(r.email(), r.name(), r.password());
  }

  @PostMapping("/api/auth/login")
  @Operation(summary = "Issue a 15-minute access token")
  public AccountsService.Token login(@Valid @RequestBody Login r) {
    return service.login(r.email(), r.password());
  }

  @GetMapping("/api/me")
  @Operation(summary = "Read own profile and wallet status")
  public AccountsService.Profile me(@AuthenticationPrincipal Jwt jwt) {
    return service.me(UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/internal/wallets/resolve")
  @Operation(summary = "Resolve a recipient for payments-service")
  public WalletResolution resolve(@RequestParam String email) {
    return view(service.resolve(email));
  }

  @GetMapping("/internal/users/{id}/wallet")
  @Operation(summary = "Resolve a user wallet for payments-service")
  public WalletResolution wallet(@PathVariable UUID id) {
    return view(service.wallet(id));
  }

  private WalletResolution view(WalletRepository.Wallet w) {
    return new WalletResolution(w.userId(), w.id(), w.currency(), w.status());
  }
}
