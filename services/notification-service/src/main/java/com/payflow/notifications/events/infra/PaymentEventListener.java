package com.payflow.notifications.events.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.contracts.PaymentEvent;
import com.payflow.notifications.notification.domain.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {
  private final ObjectMapper json;
  private final NotificationService service;
  private final MeterRegistry metrics;

  public PaymentEventListener(
      ObjectMapper json, NotificationService service, MeterRegistry metrics) {
    this.json = json;
    this.service = service;
    this.metrics = metrics;
  }

  @KafkaListener(topics = "payments.events")
  public void consume(String value) {
    PaymentEvent event;
    try {
      event = json.readValue(value, PaymentEvent.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Malformed payment event");
    }
    if (service.accept(event)) metrics.counter("payflow.notifications.processed").increment();
  }
}
