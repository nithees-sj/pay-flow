package com.payflow.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceFilter extends OncePerRequestFilter {
  public static String trace(HttpServletRequest r) {
    Object v = r.getAttribute("traceId");
    return v == null ? UUID.randomUUID().toString() : v.toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = req.getHeader("X-Trace-Id");
    try {
      id = UUID.fromString(id).toString();
    } catch (Exception ignored) {
      id = UUID.randomUUID().toString();
    }
    req.setAttribute("traceId", id);
    res.setHeader("X-Trace-Id", id);
    MDC.put("traceId", id);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("traceId");
    }
  }
}
