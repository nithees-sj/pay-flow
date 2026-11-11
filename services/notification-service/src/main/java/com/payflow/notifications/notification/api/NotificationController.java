package com.payflow.notifications.notification.api;

import com.payflow.notifications.notification.infra.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {
  private final NotificationRepository repo;

  public NotificationController(NotificationRepository repo) {
    this.repo = repo;
  }

  public record MarkRead(@NotNull @AssertTrue Boolean read) {}

  @GetMapping("/api/notifications")
  @Operation(summary = "Read own notification inbox")
  public NotificationRepository.Inbox inbox(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean unread) {
    return repo.inbox(UUID.fromString(jwt.getSubject()), page, size, unread);
  }

  @PatchMapping("/api/notifications/{id}")
  @Operation(summary = "Idempotently mark own notification read")
  public NotificationRepository.Notification read(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody MarkRead request) {
    return repo.markRead(id, UUID.fromString(jwt.getSubject()));
  }
}
