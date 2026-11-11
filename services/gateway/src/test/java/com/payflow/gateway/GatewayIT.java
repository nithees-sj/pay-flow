package com.payflow.gateway;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {
  @Container
  static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

  @LocalServerPort int port;
  @Autowired JwtEncoder encoder;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("payflow.jwt-secret", () -> "test-jwt-secret-at-least-32-characters-long");
    r.add("payflow.accounts-token", () -> "test-accounts");
    r.add("payflow.payments-token", () -> "test-payments");
    r.add("payflow.rate-limit", () -> 2);
  }

  @Test
  void rejectsMissingAndInvalidJwt() {
    var c = client();
    for (String value : List.of("", "Bearer invalid"))
      c.get()
          .uri("/api/gateway/ping")
          .header("Authorization", value)
          .header("X-User-Id", UUID.randomUUID().toString())
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("UNAUTHORIZED");
  }

  @Test
  void validatesJwtAndLimitsPerUser() {
    String token = token();
    var c = client();
    for (int i = 0; i < 2; i++)
      c.get()
          .uri("/api/gateway/ping")
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isOk();
    c.get()
        .uri("/api/gateway/ping")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isEqualTo(429);
  }

  @Test
  void deniesBrowserInternalRoutes() {
    client()
        .get()
        .uri("/internal/transfers/" + UUID.randomUUID())
        .headers(h -> h.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  private WebTestClient client() {
    return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  private String token() {
    Instant now = Instant.now();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("payflow")
                    .subject(UUID.randomUUID().toString())
                    .audience(List.of("payflow-api"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(900))
                    .build()))
        .getTokenValue();
  }
}
