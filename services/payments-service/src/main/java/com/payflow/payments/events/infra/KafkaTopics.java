package com.payflow.payments.events.infra;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {
  @Bean
  NewTopic paymentEvents() {
    return TopicBuilder.name("payments.events").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic paymentDeadLetters() {
    return TopicBuilder.name("payments.events.DLT").partitions(3).replicas(1).build();
  }
}
