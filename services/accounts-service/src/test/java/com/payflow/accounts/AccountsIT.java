package com.payflow.accounts;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.accounts.wallet.domain.WalletProvisioner;
import com.payflow.accounts.wallet.infra.WalletRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AccountsIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
  static HttpServer ledger;
  static volatile boolean available = true;

  static {
    try {
      ledger = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ledger.createContext(
          "/internal/accounts/",
          e -> {
            int code = available ? 200 : 503;
            e.sendResponseHeaders(code, -1);
            e.close();
          });
      ledger.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("payflow.jwt-secret", () -> "test-jwt-secret-at-least-32-characters-long");
    r.add("payflow.accounts-token", () -> "test-accounts");
    r.add("payflow.payments-token", () -> "test-payments");
    r.add("payflow.ledger-url", () -> "http://127.0.0.1:" + ledger.getAddress().getPort());
    r.add("payflow.provision-delay", () -> "3600000");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired WalletProvisioner provisioner;
  @Autowired WalletRepository wallets;

  @AfterAll
  static void stop() {
    ledger.stop(0);
  }

  @Test
  void registerLoginAndReadOwnProfile() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    mvc.perform(post("/api/auth/register").contentType("application/json").content(body(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.wallet.status").value("ACTIVE"));
    String response =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"long-test-password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(response).get("accessToken").asText();
    mvc.perform(
            get("/api/me")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email));
    mvc.perform(post("/api/auth/register").contentType("application/json").content(body(email)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void provisioningRecoversAfterLedgerOutage() throws Exception {
    available = false;
    String result;
    try {
      result =
          mvc.perform(
                  post("/api/auth/register")
                      .contentType("application/json")
                      .content(body(UUID.randomUUID() + "@example.test")))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.wallet.status").value("PROVISIONING"))
              .andReturn()
              .getResponse()
              .getContentAsString();
    } finally {
      available = true;
    }
    UUID user = UUID.fromString(json.readTree(result).get("user").get("id").asText());
    provisioner.provision(wallets.byUser(user).orElseThrow());
    assertThat(wallets.byUser(user).orElseThrow().status()).isEqualTo("ACTIVE");
  }

  @Test
  void protectsPublicAndInternalIdentity() throws Exception {
    mvc.perform(get("/api/me").header("X-User-Id", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/users/" + UUID.randomUUID() + "/wallet"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType("application/json").content(body("invalid")))
        .andExpect(status().isBadRequest());
  }

  private static String body(String email) {
    return "{\"email\":\"" + email + "\",\"name\":\"Tester\",\"password\":\"long-test-password\"}";
  }
}
