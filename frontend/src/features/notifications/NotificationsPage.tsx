import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bell, ArrowUpRight, Check } from "lucide-react";
import { useInbox } from "../../api/queries";
import { api, errorMessage } from "../../api/client";
import type { Notification } from "../../api/types";
import { formatMoney } from "../../lib/money";
import { dateTime } from "../../lib/format";
import {
  EmptyState,
  ErrorNotice,
  Skeleton,
  useToast,
} from "../../components/Feedback";
import { Pagination } from "../../components/Pagination";
export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unread, setUnread] = useState(false);
  const query = useInbox(page, unread);
  const cache = useQueryClient();
  const toast = useToast();
  const mutation = useMutation({
    mutationFn: (id: string) =>
      api<Notification>(`/notifications/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ read: true }),
      }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["notifications"] });
    },
    onError: (e) => toast(errorMessage(e), true),
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">YOU’RE IN THE LOOP</span>
          <h1>Your notifications</h1>
          <p>The latest on your money, without the noise.</p>
        </div>
        <span className="date-pill">
          <Bell size={16} />
          {query.data?.unreadCount ?? 0} unread
        </span>
      </div>
      <section className="panel notifications-panel">
        <div className="transaction-toolbar">
          <div className="filter-tabs">
            <button
              className={!unread ? "selected" : ""}
              onClick={() => {
                setUnread(false);
                setPage(0);
              }}
            >
              All updates
            </button>
            <button
              className={unread ? "selected" : ""}
              onClick={() => {
                setUnread(true);
                setPage(0);
              }}
            >
              Unread
            </button>
          </div>
        </div>
        {query.isPending ? (
          <Skeleton />
        ) : query.error ? (
          <ErrorNotice>{errorMessage(query.error)}</ErrorNotice>
        ) : query.data?.items.length ? (
          <>
            <ul className="notification-list">
              {query.data.items.map((n) => (
                <li key={n.id} className={n.readAt ? "" : "unread"}>
                  <span className="notification-symbol">
                    <Bell size={20} />
                  </span>
                  <div className="notification-copy">
                    <strong>
                      {n.type === "payment.completed"
                        ? n.messageParameters.direction === "INCOMING"
                          ? "A little more in your wallet."
                          : "Your money has arrived."
                        : "An update on your transfer."}
                      {!n.readAt && <span className="unread-indicator" />}
                    </strong>
                    <p>
                      {formatMoney(n.messageParameters.amount)} ·{" "}
                      {n.type === "payment.completed"
                        ? n.messageParameters.direction === "INCOMING"
                          ? "Payment received"
                          : "Payment completed"
                        : n.type === "payment.declined"
                          ? "Payment declined"
                          : "Payment failed"}
                    </p>
                    <time>{dateTime(n.createdAt)}</time>
                    <div className="notification-actions">
                      <Link
                        className="text-link"
                        to={`/transactions/${n.paymentId}`}
                      >
                        View transfer <ArrowUpRight size={14} />
                      </Link>
                      {!n.readAt && (
                        <button
                          className="text-link"
                          disabled={mutation.isPending}
                          onClick={() => mutation.mutate(n.id)}
                        >
                          <Check size={15} />
                          Mark as read
                        </button>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={page}
              totalPages={query.data.totalPages}
              onChange={setPage}
            />
          </>
        ) : (
          <EmptyState
            title={unread ? "You’re all caught up." : "A quiet moment."}
            message={
              unread
                ? "No unread updates. We’ll keep you posted."
                : "Updates about your transfers will arrive right here."
            }
          />
        )}
      </section>
    </>
  );
}
