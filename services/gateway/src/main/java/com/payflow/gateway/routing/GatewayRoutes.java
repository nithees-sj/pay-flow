package com.payflow.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration
public class GatewayRoutes {
  @Bean
  RouteLocator routes(
      RouteLocatorBuilder b,
      @Value("${ACCOUNTS_URL:http://localhost:8081}") String accounts,
      @Value("${LEDGER_URL:http://localhost:8082}") String ledger,
      @Value("${PAYMENTS_URL:http://localhost:8083}") String payments,
      @Value("${NOTIFICATIONS_URL:http://localhost:8084}") String notifications) {
    return b.routes()
        .route(
            "accounts", r -> r.path("/api/auth/**", "/api/me", "/api/accounts/ping").uri(accounts))
        .route("payments", r -> r.path("/api/payments/**", "/api/wallet/topups").uri(payments))
        .route(
            "ledger",
            r -> r.path("/api/wallet", "/api/wallet/entries", "/api/ledger/ping").uri(ledger))
        .route("notifications", r -> r.path("/api/notifications/**").uri(notifications))
        .build();
  }
}
