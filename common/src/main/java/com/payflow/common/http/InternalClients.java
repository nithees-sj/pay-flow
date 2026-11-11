package com.payflow.common.http;

import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class InternalClients {
  private InternalClients() {}

  public static RestClient create(String base, String caller, String token) {
    if (token == null || token.isBlank())
      throw new IllegalArgumentException("Internal caller credential is required");
    var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(3));
    return RestClient.builder()
        .baseUrl(base)
        .requestFactory(factory)
        .defaultHeader("X-Service-Name", caller)
        .defaultHeader("X-Service-Token", token)
        .build();
  }
}
