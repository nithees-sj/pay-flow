package com.payflow.common.error;

import java.util.Map;
import org.springframework.http.ProblemDetail;

public final class Problems {
  private Problems() {}

  public static ProblemDetail create(int status, String code, String message, String traceId) {
    var p = ProblemDetail.forStatus(status);
    p.setTitle("Request rejected");
    p.setDetail(message);
    p.setProperty("code", code);
    p.setProperty("message", message);
    p.setProperty("traceId", traceId);
    p.setProperty("details", Map.of());
    return p;
  }
}
