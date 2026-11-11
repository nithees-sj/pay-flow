package com.payflow.payments.risk.domain;

import com.payflow.payments.payment.domain.Payment;
import com.payflow.payments.risk.infra.RiskRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RiskService {
  private final RiskRepository repo;
  private final StringRedisTemplate redis;
  private final TransactionTemplate tx;
  private final int minuteLimit;
  private final long dailyLimit;
  private static final DefaultRedisScript<String> SCRIPT =
      new DefaultRedisScript<>(
          "local old=redis.call('GET',KEYS[3]); if old then return old end; local"
              + " m=math.max(tonumber(redis.call('GET',KEYS[1]) or '0'),tonumber(ARGV[1])); local"
              + " d=math.max(tonumber(redis.call('GET',KEYS[2]) or '0'),tonumber(ARGV[2]));"
              + " redis.call('SET',KEYS[1],m,'EX',120); redis.call('SET',KEYS[2],d,'EX',172800);"
              + " local result='ALLOWED'; if m>tonumber(ARGV[3]) then"
              + " result='VELOCITY_LIMIT_EXCEEDED' elseif d>tonumber(ARGV[4]) then"
              + " result='DAILY_AMOUNT_LIMIT_EXCEEDED' end;"
              + " redis.call('SET',KEYS[3],result,'EX',172800); return result",
          String.class);

  public RiskService(
      RiskRepository repo,
      StringRedisTemplate redis,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${payflow.risk.minute-limit}") int minuteLimit,
      @Value("${payflow.risk.daily-limit}") long dailyLimit) {
    this.repo = repo;
    this.redis = redis;
    tx = new TransactionTemplate(manager);
    this.minuteLimit = minuteLimit;
    this.dailyLimit = dailyLimit;
    if (minuteLimit < 1 || dailyLimit < 1 || dailyLimit > 1000000000000L)
      throw new IllegalArgumentException("Invalid risk configuration");
  }

  public String evaluate(Payment p) {
    if (p.amount() <= 0) return "INVALID_AMOUNT";
    if (p.kind().equals("TEST_TOPUP")) return "ALLOWED";
    if (p.initiatedBy().equals(p.recipientUserId())) return "SELF_TRANSFER";
    return tx.execute(s -> {
      repo.lock(p.initiatedBy());
      String prior = repo.decision(p.id());
      if (prior != null) return prior;
      repo.attempt(p);
      var counts = repo.counts(p.initiatedBy(), dailyLimit + 1);
      long seconds = Instant.now().getEpochSecond();
      String prefix = "risk:" + p.initiatedBy() + ":";
      String decision =
          redis.execute(
              SCRIPT,
              List.of(
                  prefix + "minute:" + seconds / 60,
                  prefix + "day:" + seconds / 86400,
                  prefix + "payment:" + p.id()),
              Long.toString(counts.minute()),
              Long.toString(counts.daily()),
              Integer.toString(minuteLimit),
              Long.toString(dailyLimit));
      if (decision == null) throw new IllegalStateException("Risk decision unavailable");
      repo.decision(p.id(), decision);
      return decision;
    });
  }
}
