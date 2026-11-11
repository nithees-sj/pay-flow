package com.payflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.Problems;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletSecurity {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      ObjectMapper mapper,
      @Value("${payflow.accounts-token}") String accountsToken,
      @Value("${payflow.payments-token}") String paymentsToken)
      throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.requestMatchers(
                    "/actuator/health/**",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api/auth/register",
                    "/api/auth/login")
                .permitAll()
                .requestMatchers("/internal/**")
                .hasAuthority("SERVICE")
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(o ->
            o.jwt(j -> {})
                .authenticationEntryPoint(
                    (req, res, e) -> write(mapper, req, res, 401, "UNAUTHORIZED")))
        .exceptionHandling(e ->
            e.authenticationEntryPoint(
                    (req, res, x) -> write(mapper, req, res, 401, "UNAUTHORIZED"))
                .accessDeniedHandler((req, res, x) -> write(mapper, req, res, 403, "FORBIDDEN")))
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                if (req.getRequestURI().startsWith("/internal/")) {
                  String caller = req.getHeader("X-Service-Name"),
                      token = req.getHeader("X-Service-Token");
                  boolean provisioning =
                      req.getMethod().equals("PUT")
                          && req.getRequestURI().startsWith("/internal/accounts/");
                  String expected = provisioning ? accountsToken : paymentsToken;
                  if (!(provisioning ? "accounts" : "payments").equals(caller)
                      || expected.isBlank()
                      || token == null
                      || !MessageDigest.isEqual(
                          expected.getBytes(StandardCharsets.UTF_8),
                          token.getBytes(StandardCharsets.UTF_8))) {
                    write(mapper, req, res, 401, "UNAUTHORIZED");
                    return;
                  }
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              caller, null, List.of(new SimpleGrantedAuthority("SERVICE"))));
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static void write(
      ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res, int status, String code)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    mapper.writeValue(
        res.getOutputStream(),
        Problems.create(status, code, "Authentication or access denied", TraceFilter.trace(req)));
  }
}
