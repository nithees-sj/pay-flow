package com.payflow.accounts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.payflow.accounts.auth.domain.AccountsService;
import com.payflow.accounts.auth.infra.UserRepository;
import com.payflow.accounts.wallet.domain.WalletProvisioner;
import com.payflow.accounts.wallet.infra.WalletRepository;
import com.payflow.common.error.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class AccountsServiceTest {
  @Test
  void canonicalEmailIsStable() {
    assertThat(AccountsService.canonicalEmail("  User@Example.Test "))
        .isEqualTo("user@example.test");
  }

  @Test
  void missingUserHasGenericAuthenticationFailure() {
    var users = mock(UserRepository.class);
    when(users.findByEmail(anyString())).thenReturn(Optional.empty());
    var service = service(users);
    assertThatThrownBy(() -> service.login("missing@example.test", "long-test-password"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(401);
              assertThat(e.getMessage()).isEqualTo("Invalid credentials");
            });
  }

  @Test
  void rejectsPasswordBeyondBcryptByteLimitBeforePersistence() {
    var users = mock(UserRepository.class);
    var service = service(users);
    assertThatThrownBy(() -> service.register("user@example.test", "User", "界".repeat(30)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(users);
  }

  private AccountsService service(UserRepository users) {
    return new AccountsService(
        users,
        mock(WalletRepository.class),
        mock(WalletProvisioner.class),
        mock(PlatformTransactionManager.class),
        mock(JwtEncoder.class));
  }
}
