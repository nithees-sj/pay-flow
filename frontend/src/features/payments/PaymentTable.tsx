import { Link } from "react-router-dom";
import { ArrowDownLeft, ArrowUpRight, Plus, ChevronRight } from "lucide-react";
import type { Payment } from "../../api/types";
import { formatMoney } from "../../lib/money";
import { dateTime } from "../../lib/format";
import { StatusBadge } from "../../components/StatusBadge";
export function paymentLabel(payment: Payment) {
  return payment.kind === "TEST_TOPUP"
    ? "Test funds added"
    : payment.direction === "INCOMING"
      ? "Payment received"
      : (payment.recipientEmail ?? "Payment sent");
}
export function PaymentTable({ payments }: { payments: Payment[] }) {
  return (
    <div className="table-scroll">
      <table className="payment-table">
        <thead>
          <tr>
            <th>Transaction</th>
            <th>Date</th>
            <th>Status</th>
            <th className="align-right">Amount</th>
            <th>
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {payments.map((p) => (
            <tr key={p.id}>
              <td>
                <Link className="transaction-name" to={`/transactions/${p.id}`}>
                  <span
                    className={`transaction-icon ${p.direction.toLowerCase()}`}
                  >
                    {p.kind === "TEST_TOPUP" ? (
                      <Plus size={20} />
                    ) : p.direction === "INCOMING" ? (
                      <ArrowDownLeft size={21} />
                    ) : (
                      <ArrowUpRight size={21} />
                    )}
                  </span>
                  <span>
                    <strong>{paymentLabel(p)}</strong>
                    <small>
                      {p.note ||
                        (p.kind === "TEST_TOPUP"
                          ? "Demo wallet funding"
                          : p.direction === "INCOMING"
                            ? "Wallet transfer · Received"
                            : "Wallet transfer · Sent")}
                    </small>
                  </span>
                </Link>
              </td>
              <td className="date-cell">{dateTime(p.createdAt)}</td>
              <td>
                <StatusBadge status={p.status} />
              </td>
              <td
                className={`transaction-amount align-right ${p.direction === "INCOMING" && p.status === "COMPLETED" ? "positive" : ""}`}
              >
                {p.status === "COMPLETED"
                  ? p.direction === "INCOMING"
                    ? "+"
                    : "−"
                  : ""}
                {formatMoney(p.amount)}
              </td>
              <td>
                <Link
                  className="icon-button"
                  to={`/transactions/${p.id}`}
                  aria-label={`View ${paymentLabel(p)}`}
                >
                  <ChevronRight size={17} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
