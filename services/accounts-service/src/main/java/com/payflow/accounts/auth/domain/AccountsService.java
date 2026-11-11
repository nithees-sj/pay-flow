package com.payflow.accounts.auth.domain;

import com.payflow.accounts.auth.infra.UserRepository;
import com.payflow.accounts.wallet.domain.WalletProvisioner;
import com.payflow.accounts.wallet.infra.WalletRepository;
import com.payflow.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountsService {
  private final UserRepository users;
  private final WalletRepository wallets;
  private final WalletProvisioner provisioner;
  private final TransactionTemplate tx;
  private final JwtEncoder encoder;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final String dummy = passwords.encode("not-an-actual-user-password");

  public AccountsService(
      UserRepository users,
      WalletRepository wallets,
      WalletProvisioner provisioner,
      org.springframework.transaction.PlatformTransactionManager manager,
      JwtEncoder encoder) {
    this.users = users;
    this.wallets = wallets;
    this.provisioner = provisioner;
    this.tx = new TransactionTemplate(manager);
    this.encoder = encoder;
  }

  public record UserView(UUID id, String email, String name) {}

  public record WalletView(UUID id, String currency, String status) {}

  public record Profile(UserView user, WalletView wallet) {}

  public record Token(String accessToken, String tokenType, int expiresIn) {}

  public Profile register(String email, String name, String password) {
    validatePassword(password);
    String canonical = canonicalEmail(email);
    UUID id = UUID.randomUUID(), wallet = UUID.randomUUID();
    String hash = passwords.encode(password);
    try {
      tx.executeWithoutResult(s -> {
        users.saveAndFlush(new User(id, canonical, name.trim(), hash));
        wallets.create(wallet, id);
      });
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(409, "EMAIL_ALREADY_EXISTS", "Email already registered");
    }
    provisioner.provision(wallets.byUser(id).orElseThrow());
    return me(id);
  }

  public Token login(String email, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(401, "UNAUTHORIZED", "Invalid credentials");
    var user = users.findByEmail(canonicalEmail(email));
    boolean matches = passwords.matches(password, user.map(User::getPasswordHash).orElse(dummy));
    if (user.isEmpty() || !matches)
      throw new ApiException(401, "UNAUTHORIZED", "Invalid credentials");
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("payflow")
            .subject(user.get().getId().toString())
            .audience(List.of("payflow-api"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();
    return new Token(
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "Bearer",
        900);
  }

  public Profile me(UUID id) {
    var user =
        users.findById(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "User not found"));
    var wallet = wallets.byUser(id).orElseThrow();
    return new Profile(
        new UserView(user.getId(), user.getEmail(), user.getName()),
        new WalletView(wallet.id(), wallet.currency(), wallet.status()));
  }

  public WalletRepository.Wallet wallet(UUID user) {
    var w =
        wallets
            .byUser(user)
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "User not found"));
    if (!w.status().equals("ACTIVE"))
      throw new ApiException(409, "WALLET_NOT_READY", "Wallet provisioning in progress");
    return w;
  }

  public WalletRepository.Wallet resolve(String email) {
    return wallet(
        users
            .findByEmail(canonicalEmail(email))
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Recipient not found"))
            .getId());
  }

  private static void validatePassword(String password) {
    int n = password.getBytes(StandardCharsets.UTF_8).length;
    if (n < 12 || n > 72) throw new IllegalArgumentException("Password must be 12–72 UTF-8 bytes");
  }

  public static String canonicalEmail(String s) {
    return s.strip().toLowerCase(Locale.ROOT);
  }
}
