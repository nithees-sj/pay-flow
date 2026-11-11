package com.payflow.accounts.auth.infra;

import com.payflow.accounts.auth.domain.User;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);
}
