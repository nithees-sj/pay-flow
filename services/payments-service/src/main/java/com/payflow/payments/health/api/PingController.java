package com.payflow.payments.health.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class PingController {
  @GetMapping("/api/payments/ping")
  @Operation(summary = "Check service routing")
  public Map<String, String> ping() {
    return Map.of("service", "payments", "status", "ok");
  }
}
