import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowUpRight,
  ArrowDownLeft,
  Check,
  Clock3,
  Copy,
} from "lucide-react";
import { api, errorMessage } from "../../api/client";
import type { Payment } from "../../api/types";
import { formatMoney } from "../../lib/money";
import { dateTime, reasonMessage } from "../../lib/format";
import { ErrorNotice, Skeleton, useToast } from "../../components/Feedback";
import { StatusBadge } from "../../components/StatusBadge";
import { paymentLabel } from "./PaymentTable";
export function PaymentDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const query = useQuery({
    queryKey: ["payment", id],
    queryFn: () => api<Payment>(`/payments/${id}`),
    refetchInterval: (q) => (q.state.data?.status === "CREATED" ? 2000 : false),
  });
  const p = query.data;
  return (
    <>
      <Link className="text-link back-link" to="/transactions">
        <ArrowLeft size={16} />
        All transactions
      </Link>
      <div className="page-heading">
        <div>
          <h1>Transfer details</h1>
          <p>Every step, from start to finish.</p>
        </div>
      </div>
      {query.isPending ? (
        <Skeleton />
      ) : query.error ? (
        <ErrorNotice>{errorMessage(query.error)}</ErrorNotice>
      ) : (
        p && (
          <div className="detail-layout">
            <section className="panel receipt">
              <span
                className={`transaction-icon receipt-icon ${p.direction.toLowerCase()}`}
              >
                {p.direction === "INCOMING" ? (
                  <ArrowDownLeft size={30} />
                ) : (
                  <ArrowUpRight size={30} />
                )}
              </span>
              <p className="muted">{paymentLabel(p)}</p>
              <h2>{formatMoney(p.amount)}</h2>
              <StatusBadge status={p.status} />
              {p.status === "CREATED" && (
                <div className="info-banner">
                  We’re confirming the result. Please don’t send this payment
                  again; this page updates automatically.
                </div>
              )}
              {p.reasonCode && (
                <ErrorNotice>{reasonMessage(p.reasonCode)}</ErrorNotice>
              )}
              <dl className="detail-list">
                <div>
                  <dt>Direction</dt>
                  <dd>{p.direction === "INCOMING" ? "Received" : "Sent"}</dd>
                </div>
                <div>
                  <dt>Type</dt>
                  <dd>
                    {p.kind === "TEST_TOPUP"
                      ? "Test top-up"
                      : "Wallet transfer"}
                  </dd>
                </div>
                {p.recipientEmail && (
                  <div>
                    <dt>Recipient</dt>
                    <dd>{p.recipientEmail}</dd>
                  </div>
                )}
                {p.note && (
                  <div>
                    <dt>Note</dt>
                    <dd>{p.note}</dd>
                  </div>
                )}
                <div>
                  <dt>Created</dt>
                  <dd>{dateTime(p.createdAt)}</dd>
                </div>
                <div className="id-row">
                  <dt>Payment ID</dt>
                  <dd>
                    <code>{p.id}</code>
                    <button
                      className="icon-button"
                      aria-label="Copy payment ID"
                      onClick={() => {
                        void navigator.clipboard
                          .writeText(p.id)
                          .then(() => toast("Payment ID copied."))
                          .catch(() => toast("Copy the ID shown above.", true));
                      }}
                    >
                      <Copy size={14} />
                    </button>
                  </dd>
                </div>
              </dl>
            </section>
            <section className="panel timeline-panel">
              <h2>Transfer timeline</h2>
              <p className="muted">A record you can follow.</p>
              <ol className="timeline">
                {p.transitions.map((t, i) => (
                  <li key={`${t.toStatus}-${i}`}>
                    <span
                      className={
                        t.toStatus === "COMPLETED"
                          ? "timeline-dot complete"
                          : "timeline-dot"
                      }
                    >
                      <Check size={14} />
                    </span>
                    <strong>
                      {t.toStatus === "CREATED"
                        ? "Transfer created"
                        : t.toStatus === "COMPLETED"
                          ? "Transfer completed"
                          : t.toStatus === "DECLINED"
                            ? "Transfer declined"
                            : "Transfer failed"}
                    </strong>
                    <time>{dateTime(t.occurredAt)}</time>
                    {t.reasonCode && <p>{reasonMessage(t.reasonCode)}</p>}
                  </li>
                ))}
                {p.status === "CREATED" && (
                  <li>
                    <span className="timeline-dot pending">
                      <Clock3 size={14} />
                    </span>
                    <strong>Confirming the outcome</strong>
                    <p>Checking with your wallet. No action needed.</p>
                  </li>
                )}
              </ol>
            </section>
          </div>
        )
      )}
    </>
  );
}
