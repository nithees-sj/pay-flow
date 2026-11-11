package com.payflow.payments.payment.domain;

public enum PaymentStatus {
  CREATED,
  COMPLETED,
  FAILED,
  DECLINED;

  public void requireTransitionTo(PaymentStatus target) {
    if (this != CREATED || target == CREATED)
      throw new IllegalStateException("Illegal payment transition");
  }
}
