package com.payflow.common.money;

import java.util.Currency;
import java.util.Objects;

/** Exact minor-unit value. Wire adapters serialize amounts as decimal strings. */
public record Money(long minor, String currency) {
  public Money {
    Objects.requireNonNull(currency, "currency");
    Currency.getInstance(currency);
  }

  public Money add(Money other) {
    requireCurrency(other);
    return new Money(Math.addExact(minor, other.minor), currency);
  }

  public Money subtract(Money other) {
    requireCurrency(other);
    return new Money(Math.subtractExact(minor, other.minor), currency);
  }

  private void requireCurrency(Money other) {
    if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
  }

  public static long parse(String value) {
    if (value == null || !value.matches("-?(0|[1-9][0-9]*)"))
      throw new IllegalArgumentException("Expected minor-unit decimal string");
    return Long.parseLong(value);
  }
}
