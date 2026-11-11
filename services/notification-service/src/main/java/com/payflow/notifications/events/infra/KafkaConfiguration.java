package com.payflow.notifications.events.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfiguration {
  @Bean
  NewTopic events() {
    return TopicBuilder.name("payments.events").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetters() {
    return TopicBuilder.name("payments.events.DLT").partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    var publisher =
        new DeadLetterPublishingRecoverer(
            kafka, (record, e) -> new TopicPartition("payments.events.DLT", record.partition()));
    publisher.setFailIfSendResultIsError(true);
    publisher.setWaitForSendResultTimeout(Duration.ofSeconds(15));
    var backoff = new ExponentialBackOffWithMaxRetries(2);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(5);
    backoff.setMaxInterval(5000);
    return new DefaultErrorHandler(
        (record, e) -> {
          publisher.accept(record, e);
          metrics.counter("payflow.notifications.deadletters").increment();
        },
        backoff);
  }
}
