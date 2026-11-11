package com.payflow.common.money;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void addsExactly() {
    assertThat(new Money(10, "INR").add(new Money(20, "INR"))).isEqualTo(new Money(30, "INR"));
  }

  @Test
  void rejectsOverflow() {
    assertThatThrownBy(() -> new Money(Long.MAX_VALUE, "INR").add(new Money(1, "INR")))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void rejectsCurrencyMismatch() {
    assertThatThrownBy(() -> new Money(1, "INR").subtract(new Money(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesBeyondJavascriptPrecision() {
    assertThat(Money.parse("9007199254740993")).isEqualTo(9007199254740993L);
  }

  @Test
  void rejectsDecimalAndLeadingZeros() {
    for (String s : new String[] {"1.2", "01", "1e3", "9223372036854775808"})
      assertThatThrownBy(() -> Money.parse(s)).isInstanceOf(IllegalArgumentException.class);
  }
}
