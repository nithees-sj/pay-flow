import { execFileSync } from "node:child_process";
import { writeFileSync } from "node:fs";
function query(database, sql) {
  return execFileSync(
    "docker",
    [
      "compose",
      "-f",
      "infra/docker-compose.yml",
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "postgres",
      "-d",
      database,
      "-At",
      "-c",
      sql,
    ],
    { encoding: "utf8" },
  ).trim();
}
const result = {
  checkedAt: new Date().toISOString(),
  signedEntrySum: query(
    "ledger_db",
    "SELECT coalesce(sum(CASE WHEN direction='CREDIT' THEN amount_minor::numeric ELSE -amount_minor::numeric END),0) FROM ledger_entries",
  ),
  negativeUserWallets: Number(
    query(
      "ledger_db",
      "SELECT count(*) FROM ledger_accounts WHERE kind='USER' AND balance_minor<0",
    ),
  ),
  cachedBalanceMismatches: Number(
    query(
      "ledger_db",
      "SELECT count(*) FROM ledger_accounts a WHERE a.balance_minor::numeric<>(SELECT coalesce(sum(CASE WHEN e.direction='CREDIT' THEN e.amount_minor::numeric ELSE -e.amount_minor::numeric END),0) FROM ledger_entries e WHERE e.account_id=a.id)",
    ),
  ),
  malformedPostedTransfers: Number(
    query(
      "ledger_db",
      "SELECT count(*) FROM transfers t WHERE status='POSTED' AND ((SELECT count(*) FROM ledger_entries e WHERE e.transfer_id=t.transfer_id)<>2 OR (SELECT count(*) FROM ledger_entries e WHERE e.transfer_id=t.transfer_id AND e.amount_minor=t.amount_minor AND e.currency=t.currency AND ((e.direction='DEBIT' AND e.account_id=t.from_account_id) OR(e.direction='CREDIT' AND e.account_id=t.to_account_id)))<>2)",
    ),
  ),
  rejectedTransfersWithEntries: Number(
    query(
      "ledger_db",
      "SELECT count(*) FROM transfers t WHERE status='REJECTED' AND EXISTS(SELECT 1 FROM ledger_entries e WHERE e.transfer_id=t.transfer_id)",
    ),
  ),
  duplicateActiveIdempotencyScopes: Number(
    query(
      "payments_db",
      "SELECT count(*) FROM(SELECT user_id,operation,key_hash FROM idempotency_records WHERE expires_at>now() GROUP BY user_id,operation,key_hash HAVING count(*)>1)x",
    ),
  ),
  terminalPaymentsWithoutOutbox: Number(
    query(
      "payments_db",
      "SELECT count(*) FROM payments p WHERE status<>'CREATED' AND NOT EXISTS(SELECT 1 FROM outbox_events o WHERE o.payment_id=p.id)",
    ),
  ),
  ledgerTransfers: Number(query("ledger_db", "SELECT count(*) FROM transfers")),
  ledgerEntries: Number(
    query("ledger_db", "SELECT count(*) FROM ledger_entries"),
  ),
};
const paymentTransfers = JSON.parse(
  query(
    "payments_db",
    "SELECT coalesce(json_agg(transfer_id),'[]'::json) FROM payments WHERE status='COMPLETED'",
  ),
);
const posted = new Set(
  JSON.parse(
    query(
      "ledger_db",
      "SELECT coalesce(json_agg(transfer_id),'[]'::json) FROM transfers WHERE status='POSTED'",
    ),
  ),
);
result.completedPaymentsMissingLedger = paymentTransfers.filter(
  (id) => !posted.has(id),
).length;
result.passed =
  result.signedEntrySum === "0" &&
  [
    "negativeUserWallets",
    "cachedBalanceMismatches",
    "malformedPostedTransfers",
    "rejectedTransfersWithEntries",
    "duplicateActiveIdempotencyScopes",
    "terminalPaymentsWithoutOutbox",
    "completedPaymentsMissingLedger",
  ].every((key) => result[key] === 0);
writeFileSync(
  "load-tests/invariants.json",
  JSON.stringify(result, null, 2) + "\n",
);
console.log(JSON.stringify(result, null, 2));
if (!result.passed) process.exitCode = 1;
