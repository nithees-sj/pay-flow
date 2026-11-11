# Measured local load test

Measured **11 November 2026**, completed 11:51:53 IST. These are measurements from this machine, not capacity promises.

## Environment and workload

- Docker Desktop Linux engine: 16 CPUs, 3,908,329,472 bytes (~3.64 GiB) available memory.
- Five Java 17 services, PostgreSQL 16, Redis 7 and one Kafka KRaft broker; production nginx frontend. Named volumes already contained demo data. No host databases.
- k6 2.3.0; target `http://localhost:8080` through the gateway. The full Java suite finished before measurement.
- Setup registers seven users and funds six wallets. Sustained scenario ramps to five virtual users over 10 seconds, holds for 25 seconds and ramps down over 10 seconds. Each iteration sends 100 paise and waits 100 ms.
- Separate scenario sends 20 concurrent requests with one Idempotency-Key, followed by a replay and history check.
- Load-only limits: `RISK_MINUTE_LIMIT=100000`, `RISK_DAILY_LIMIT=1000000000000`, `GATEWAY_RATE_LIMIT=100000`. Default demo limits would intentionally decline sustained traffic. Normal limits were restored after measurement.
- Browser QA also used the local demo during this run; this is a development-machine measurement, not an isolated benchmark.

## Results

| Measurement | Observed |
|---|---:|
| End-to-end run including setup/teardown | 57.19 s |
| Sustained traffic window | 45 s |
| Total HTTP requests | 975 |
| HTTP requests/second, including setup/teardown | 17.05 |
| Completed new payments in sustained scenario | 933 |
| New payments/second over the 45 s traffic window | 20.73 |
| Payment POST p50 | 82.54 ms |
| Payment POST p95 | **127.24 ms** |
| Payment POST p99 | 158.34 ms |
| Payment POST maximum | 281.37 ms |
| Unexpected error rate | **0.00%** |
| HTTP failure rate | **0.00%** |
| Checks | 955 passed, 0 failed |
| Retry storm | 20 requests → 1 payment → 1 transfer → 2 entries |

All k6 thresholds passed (exit code 0). Expected in-flight 409 duplicate responses are explicitly allowed and separately checked; fresh-payment traffic must return COMPLETED. Raw machine-readable metrics are in [summary.json](summary.json).

## Post-load correctness

[Invariant evidence](invariants.json) recorded 946 ledger transfers and 1,892 entries at 06:22:22 UTC, including previous demo activity. Signed entry sum was **0** (INR is the only allowed currency). Negative user balances, cached-balance mismatches, malformed entry pairs, rejected transfers with entries, duplicate active idempotency scopes, terminal payments without outbox rows, and completed payments without a posted ledger transfer were all **0**.

The duplicate scenario payment was `0e28a7b1-c749-4f4b-9879-67681aa59daf`, transfer `bda4a986-41a1-4611-a89a-5152860923ff`. Its sender history contained exactly one payment; an independent ledger query returned exactly two entries for that transfer. Test funding's balancing system account is intentionally negative and is excluded from the user-overdraft check.

## Reproduce

Start the full stack in one terminal, with raised limits only for measurement:

```sh
RISK_MINUTE_LIMIT=100000 RISK_DAILY_LIMIT=1000000000000 GATEWAY_RATE_LIMIT=100000 \
  docker compose -f infra/docker-compose.yml --profile all up --build
```

In another terminal, with k6 available on PATH:

```sh
k6 run load-tests/payments.js
node scripts/check-invariants.mjs
```

Stop Compose with Ctrl+C, then start the normal command without these overrides to restore the defaults. Keep the JSON artifacts from each run before overwriting them. Longer soak tests, multi-instance failover, broker failure and production hardware capacity remain outside this local measurement.
