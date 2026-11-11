package com.payflow.payments.events.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private final OutboxRepository repo;
  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry metrics;

  public OutboxPublisher(
      OutboxRepository repo, KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    this.repo = repo;
    this.kafka = kafka;
    this.metrics = metrics;
    metrics.gauge("payflow.outbox.pending", repo, OutboxRepository::backlog);
  }

  @Scheduled(fixedDelayString = "${payflow.outbox-delay:1000}")
  public void publish() {
    for (var e : repo.claim()) {
      try {
        kafka.send("payments.events", e.key(), e.payload()).get(12, TimeUnit.SECONDS);
        repo.published(e);
      } catch (InterruptedException x) {
        Thread.currentThread().interrupt();
        repo.retry(e);
        return;
      } catch (Exception x) {
        repo.retry(e);
        metrics.counter("payflow.outbox.publish.failures").increment();
      }
    }
  }
}
