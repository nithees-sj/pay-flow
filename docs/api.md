# PayFlow API and events — v1

Public base URL: http://localhost:8080. The browser normally accesses /api through the frontend nginx/Vite proxy. JSON uses camelCase. All money is a **decimal string in minor units** ("12500" = INR 125.00), parsed with exact signed-long range checks. INR only. UUID identifiers and ISO-8601 UTC timestamps are strings. Unknown JSON fields and malformed UUID/money strings are 400. Canonical request hash uses normalized email, parsed-and-reserialized amount, currency and note (absent note becomes empty string); object property order does not matter. Strings with unnecessary leading zeros are rejected. Password 12–72 UTF-8 bytes; name 1–100; email <=254; note <=280. JWT expires in 900 seconds.

## Common responses

Problem JSON (application/problem+json):
```json
{"type":"about:blank","title":"Request rejected","status":422,"code":"IDEMPOTENCY_KEY_REUSED","message":"Key already used with a different request","traceId":"uuid","details":{}}
```
Page: `{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`. page>=0, size 1–100. Stable order createdAt DESC,id DESC. All endpoints can return 400 VALIDATION_ERROR, 401 UNAUTHORIZED, 403 FORBIDDEN, 429 RATE_LIMITED, 503 DEPENDENCY_UNAVAILABLE, 500 INTERNAL_ERROR. 404 never reveals an unauthorized object's existence. Retry-After accompanies rate limit/in-flight responses. The request X-Trace-Id is accepted only if it is a UUID; otherwise a new UUID is generated.

## Authentication and wallet

| Method / path | Auth | Request | Success | Other errors |
|---|---|---|---|---|
| POST /api/auth/register | Public, IP limited | {"email":"a@example.test","name":"A","password":"long-test-password"} | 201 {"user":{"id":"uuid","email":"a@example.test","name":"A"},"wallet":{"id":"uuid","currency":"INR","status":"ACTIVE"}} | 409 EMAIL_ALREADY_EXISTS; wallet can be PROVISIONING |
| POST /api/auth/login | Public, IP limited | {"email":"a@example.test","password":"long-test-password"} | 200 {"accessToken":"jwt","tokenType":"Bearer","expiresIn":900} | 401 UNAUTHORIZED (same response for missing user/wrong password) |
| GET /api/me | Bearer JWT | none | 200 same user/wallet structure as registration | 404 NOT_FOUND |
| GET /api/wallet | Bearer JWT | none | 200 {"accountId":"uuid","currency":"INR","balance":"10000","updatedAt":"2026-11-10T09:00:00Z"} | 409 WALLET_NOT_READY |
| GET /api/wallet/entries?page=0&size=20 | Bearer JWT | none | 200 Page of {"id":"uuid","transferId":"uuid","direction":"CREDIT","amount":"10000","currency":"INR","createdAt":"2026-11-10T09:00:00Z"} | 409 WALLET_NOT_READY |

## Payments and test funding

| Method / path | Auth | Request | Success | Other errors |
|---|---|---|---|---|
| POST /api/payments | JWT + Idempotency-Key | {"toUserEmail":"b@example.test","amount":"12500","currency":"INR","note":"Lunch"} | 201 terminal Payment; 202 CREATED with Location: /api/payments/{id} | 400 IDEMPOTENCY_KEY_REQUIRED; 409 REQUEST_IN_PROGRESS; 422 IDEMPOTENCY_KEY_REUSED, UNSUPPORTED_CURRENCY |
| POST /api/wallet/topups | JWT + Idempotency-Key | {"amount":"100000","currency":"INR"} | 201/202 Payment with kind TEST_TOPUP | same idempotency errors; 403 TEST_FUNDING_DISABLED; 422 TOPUP_LIMIT_EXCEEDED |
| GET /api/payments?page=0&size=20&status=COMPLETED&kind=PAYMENT | JWT | none; status/kind optional | 200 Page of Payment | 400 VALIDATION_ERROR |
| GET /api/payments/{id} | JWT | none | 200 Payment including transitions | 404 NOT_FOUND |

Payment:
```json
{"id":"uuid","transferId":"uuid","kind":"PAYMENT","direction":"OUTGOING","senderUserId":"uuid","recipientUserId":"uuid","recipientEmail":"b@example.test","amount":"12500","currency":"INR","note":"Lunch","status":"COMPLETED","reasonCode":null,"createdAt":"2026-11-10T09:00:00Z","updatedAt":"2026-11-10T09:00:01Z","transitions":[{"fromStatus":null,"toStatus":"CREATED","reasonCode":null,"occurredAt":"2026-11-10T09:00:00Z"},{"fromStatus":"CREATED","toStatus":"COMPLETED","reasonCode":null,"occurredAt":"2026-11-10T09:00:01Z"}]}
```

GET detail includes transitions; creation/history may also include them. Incoming history has direction INCOMING. Participants can read completed transfers; only initiator sees other states. Top-up source user is null, recipient is initiator. Unresolved recipients/account fields may be null. Financial declines are persisted payment resources (201), not transport errors. FAILED reasons include INSUFFICIENT_FUNDS, RECIPIENT_NOT_FOUND, AMOUNT_OVERFLOW. DECLINED reasons include INVALID_AMOUNT, SELF_TRANSFER, VELOCITY_LIMIT_EXCEEDED, DAILY_AMOUNT_LIMIT_EXCEEDED. Unsupported currency is rejected before creating a payment. Amount<=0 is auditable as DECLINED.

Idempotency-Key is 1–128 printable ASCII characters. Scope: user+operation+SHA256(key). Same canonical request replays original HTTP status/body/Location. A processing duplicate with no stored response returns 409. A stored 202 stays 202 even after settlement; GET shows current status. The acceptance window is exactly 24h, does not slide, and permits a new payment with that key after expiry. Ledger transfer IDs remain unique permanently. Original response persists in PostgreSQL and is cached in Redis. Rate limiting/authentication may reject a request before replay.

Default risk config: 10 unique attempts per UTC minute; 10000000 minor units per UTC day. Daily/minute counters count attempts, including ones failing in ledger, and never count key retries. Demo funding maximum 1000000 minor units per request, enabled only by DEMO_FUNDING_ENABLED=true. Sender/from-account identity is never accepted from browser input.

## Notifications and diagnostics

| Method / path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| GET /api/notifications?page=0&size=20&unread=false | JWT | none; unread optional | 200 Page plus unreadCount; items: {"id":"uuid","paymentId":"uuid","type":"payment.completed","messageKey":"payment.completed","messageParameters":{"amount":"12500","currency":"INR","direction":"INCOMING"},"createdAt":"2026-11-10T09:00:01Z","readAt":null} | common |
| PATCH /api/notifications/{id} | JWT | {"read":true} | 200 notification; repeat preserves readAt | 404 NOT_FOUND; false is 400 |
| GET /api/{service}/ping | JWT | none | 200 {"service":"accounts","status":"ok"} | common |

Ping service names: gateway, accounts, ledger, payments, notifications. Operational service endpoints: GET /actuator/health, /actuator/health/liveness, /actuator/health/readiness, /actuator/prometheus, /v3/api-docs, /swagger-ui.html. Health is unauthenticated on the service network; Swagger/metrics are local operational interfaces and are not public gateway routes. No business endpoint exposes another user's wallet/notifications.

## Internal REST (never gateway routes)

Authentication: X-Service-Name plus X-Service-Token, compared in constant time against the env credential for the allowed caller. Caller cannot set an arbitrary user context through public headers.

| Method / path | Allowed caller | Request | Response / errors |
|---|---|---|---|
| GET /internal/wallets/resolve?email=b@example.test | payments | none | 200 {"userId":"uuid","accountId":"uuid","currency":"INR","status":"ACTIVE"}; 404 NOT_FOUND, 409 WALLET_NOT_READY |
| GET /internal/users/{userId}/wallet | payments | none | same wallet response |
| PUT /internal/accounts/{accountId} | accounts | {"ownerUserId":"uuid","currency":"INR"} | 200 {"accountId":"uuid","currency":"INR"}; same body idempotent; 409 ACCOUNT_ID_REUSED |
| POST /internal/transfers | payments | {"transferId":"uuid","fromAccount":"uuid","toAccount":"uuid","amount":"12500","currency":"INR"} | 200 TransferOutcome; 409 TRANSFER_ID_REUSED; 400 malformed/invalid amount; 404 unknown account |
| GET /internal/transfers/{transferId} | payments | none | 200 TransferOutcome; 404 NOT_FOUND |
| POST /internal/accounts/{id}/topup | payments | {"transferId":"uuid","amount":"100000","currency":"INR"} | 200 TransferOutcome; funding flag/limits enforced; ledger chooses system source |

TransferOutcome: `{"transferId":"uuid","status":"POSTED","failureCode":null}` or `{"transferId":"uuid","status":"REJECTED","failureCode":"INSUFFICIENT_FUNDS"}`. Insufficient-funds rejection is durable and writes no entries. Transient infrastructure errors produce 503, not fabricated REJECTED. Reconciliation re-posts the same ID after 404. Business outcome is immutable.

## Kafka JSON schema v1

Topic payments.events; partition key paymentId; UTF-8 JSON. One terminal event per payment in outbox, potentially repeated Kafka deliveries. Required envelope fields: eventId UUID, eventType enum below, schemaVersion integer=1, occurredAt date-time, traceId UUID, data object. Required data fields: paymentId UUID, transferId UUID, kind PAYMENT/TEST_TOPUP, initiatedBy UUID, senderUserId UUID|null, recipientUserId UUID|null, amount signed-decimal-string, currency INR, status terminal enum, reasonCode string|null. No email, password, token or note.

```json
{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"payment.completed","schemaVersion":1,"occurredAt":"2026-11-10T09:00:01Z","traceId":"00000000-0000-0000-0000-000000000002","data":{"paymentId":"00000000-0000-0000-0000-000000000003","transferId":"00000000-0000-0000-0000-000000000004","kind":"PAYMENT","initiatedBy":"00000000-0000-0000-0000-000000000005","senderUserId":"00000000-0000-0000-0000-000000000005","recipientUserId":"00000000-0000-0000-0000-000000000006","amount":"12500","currency":"INR","status":"COMPLETED","reasonCode":null}}
```

- payment.completed requires status COMPLETED, amount>0, known recipient, null reason.
- payment.failed requires status FAILED and nonnull failure reason; recipient may be null.
- payment.declined requires status DECLINED and nonnull risk reason; nonpositive amount is allowed for INVALID_AMOUNT.
- No event is published for an unresolved CREATED payment.
- Additional optional properties are compatible; breaking changes require a new schemaVersion. Unknown versions and malformed payloads retry and go to payments.events.DLT.
- Completed payments notify sender and receiver; failed/declined notify initiator only; topups notify receiver once.
- DLT preserves raw original message and Spring Kafka diagnostic headers (original topic/partition/offset, exception class/message). DLT is for operators, not a second business stream. Three total processing attempts; backoff 1s then 5s. DLT ack must succeed before source offset commit.

Future RiskLens consumes v1 independently. No claim of exactly-once transport; database uniqueness makes business side effects idempotent.
