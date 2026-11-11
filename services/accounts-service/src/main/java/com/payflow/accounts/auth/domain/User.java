package com.payflow.accounts.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id private UUID id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  public User(UUID id, String email, String name, String passwordHash) {
    this.id = id;
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
    createdAt = updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }
}
