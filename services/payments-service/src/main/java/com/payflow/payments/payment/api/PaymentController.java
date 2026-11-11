package com.payflow.payments.payment.api;

import com.payflow.common.api.PageResponse;
import com.payflow.common.security.TraceFilter;
import com.payflow.payments.idempotency.domain.StoredResponse;
import com.payflow.payments.payment.domain.PaymentService;
import com.payflow.payments.payment.infra.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
  private final PaymentService service;
  private final PaymentRepository repo;

  public PaymentController(PaymentService service, PaymentRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  public record Send(
      @NotBlank @Email @Size(max = 254) String toUserEmail,
      @NotBlank String amount,
      @NotBlank String currency,
      @Size(max = 280) String note) {}

  public record Topup(@NotBlank String amount, @NotBlank String currency) {}

  @PostMapping("/api/payments")
  @Operation(summary = "Create or replay an idempotent payment")
  public ResponseEntity<?> send(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody Send r,
      HttpServletRequest request) {
    return response(
        service.submit(
            UUID.fromString(jwt.getSubject()),
            "PAYMENT",
            key,
            r.toUserEmail(),
            r.amount(),
            r.currency(),
            r.note(),
            TraceFilter.trace(request)));
  }

  @PostMapping("/api/wallet/topups")
  @Operation(summary = "Add simulated funds to own wallet")
  public ResponseEntity<?> topup(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody Topup r,
      HttpServletRequest request) {
    return response(
        service.submit(
            UUID.fromString(jwt.getSubject()),
            "TEST_TOPUP",
            key,
            null,
            r.amount(),
            r.currency(),
            null,
            TraceFilter.trace(request)));
  }

  @GetMapping("/api/payments")
  @Operation(summary = "List sent and completed received payments")
  public PageResponse<PaymentView> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String kind) {
    return repo.history(UUID.fromString(jwt.getSubject()), page, size, status, kind);
  }

  @GetMapping("/api/payments/{id}")
  @Operation(summary = "Read an owned payment and its status timeline")
  public PaymentView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return repo.visible(id, UUID.fromString(jwt.getSubject()));
  }

  private ResponseEntity<?> response(StoredResponse r) {
    return ResponseEntity.status(r.status()).header("Location", r.location()).body(r.body());
  }
}
