package com.payflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.Problems;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.security.config.web.server.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Configuration
public class GatewaySecurity {
  @Bean
  ReactiveJwtDecoder reactiveDecoder(JwtDecoder decoder) {
    return token -> Mono.fromCallable(() -> decoder.decode(token));
  }

  @Bean
  SecurityWebFilterChain security(
      ServerHttpSecurity http,
      ObjectMapper mapper,
      ReactiveStringRedisTemplate redis,
      @Value("${payflow.frontend-origin}") String origin,
      @Value("${payflow.rate-limit}") int limit) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Trace-Id"));
    cors.setExposedHeaders(List.of("Location", "Retry-After", "X-Trace-Id"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(c -> c.configurationSource(source))
        .authorizeExchange(a ->
            a.pathMatchers(
                    "/actuator/health/**",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/api/auth/login",
                    "/api/auth/register")
                .permitAll()
                .pathMatchers("/internal/**")
                .denyAll()
                .anyExchange()
                .authenticated())
        .oauth2ResourceServer(o ->
            o.jwt(j -> {})
                .authenticationEntryPoint((e, x) -> problem(e, mapper, 401, "UNAUTHORIZED")))
        .exceptionHandling(e ->
            e.authenticationEntryPoint((ex, x) -> problem(ex, mapper, 401, "UNAUTHORIZED"))
                .accessDeniedHandler((ex, x) -> problem(ex, mapper, 403, "FORBIDDEN")))
        .addFilterAfter(
            (exchange, chain) -> {
              if (!exchange.getRequest().getPath().value().startsWith("/api/"))
                return chain.filter(exchange);
              return exchange
                  .getPrincipal()
                  .map(p -> "user:" + p.getName())
                  .defaultIfEmpty(
                      "ip:"
                          + (exchange.getRequest().getRemoteAddress() == null
                              ? "unknown"
                              : exchange
                                  .getRequest()
                                  .getRemoteAddress()
                                  .getAddress()
                                  .getHostAddress()))
                  .flatMap(identity -> {
                    long minute = Instant.now().getEpochSecond() / 60;
                    String key = "gateway:" + identity + ":" + minute;
                    var script =
                        new DefaultRedisScript<Long>(
                            "local n=redis.call('INCR',KEYS[1]); if n==1 then"
                                + " redis.call('EXPIRE',KEYS[1],65) end; return n",
                            Long.class);
                    return redis
                        .execute(script, List.of(key))
                        .single()
                        .onErrorReturn(-1L)
                        .flatMap(count -> {
                          if (count < 0)
                            return problem(exchange, mapper, 503, "DEPENDENCY_UNAVAILABLE");
                          if (count > (identity.startsWith("ip:") ? 30 : limit))
                            return problem(exchange, mapper, 429, "RATE_LIMITED");
                          var request = exchange.getRequest().mutate();
                          if (identity.startsWith("user:"))
                            request.header("X-User-Id", identity.substring(5));
                          return chain.filter(exchange.mutate().request(request.build()).build());
                        });
                  });
            },
            SecurityWebFiltersOrder.AUTHORIZATION)
        .build();
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  WebFilter sanitizeIdentity() {
    return (exchange, chain) -> {
      String trace = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
      try {
        trace = UUID.fromString(trace).toString();
      } catch (Exception ignored) {
        trace = UUID.randomUUID().toString();
      }
      exchange.getAttributes().put("traceId", trace);
      exchange.getResponse().getHeaders().set("X-Trace-Id", trace);
      var request =
          exchange
              .getRequest()
              .mutate()
              .headers(h -> {
                h.remove("X-User-Id");
                h.remove("X-Service-Name");
                h.remove("X-Service-Token");
              })
              .header("X-Trace-Id", trace)
              .build();
      return chain.filter(exchange.mutate().request(request).build());
    };
  }

  private static Mono<Void> problem(
      ServerWebExchange e, ObjectMapper mapper, int status, String code) {
    e.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
    e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (status == 429) e.getResponse().getHeaders().set("Retry-After", "60");
    try {
      var bytes =
          mapper.writeValueAsBytes(
              Problems.create(
                  status,
                  code,
                  "Request could not be accepted",
                  e.getAttributeOrDefault("traceId", UUID.randomUUID().toString())));
      return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(bytes)));
    } catch (Exception x) {
      return e.getResponse().setComplete();
    }
  }
}
