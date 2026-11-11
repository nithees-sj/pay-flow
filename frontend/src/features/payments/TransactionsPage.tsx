import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, SlidersHorizontal } from "lucide-react";
import { usePayments } from "../../api/queries";
import { errorMessage } from "../../api/client";
import { EmptyState, ErrorNotice, Skeleton } from "../../components/Feedback";
import { Pagination } from "../../components/Pagination";
import { PaymentTable } from "./PaymentTable";
export function TransactionsPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const query = usePayments(page, status);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">EVERY LITTLE DETAIL</span>
          <h1>Your transactions</h1>
          <p>A clear record of everything coming in and going out.</p>
        </div>
        <Link className="button button-primary" to="/send">
          <ArrowUpRight size={18} />
          Send money
        </Link>
      </div>
      <section className="panel transactions-panel">
        <div className="transaction-toolbar">
          <div
            className="filter-tabs"
            role="group"
            aria-label="Filter transactions"
          >
            {[
              ["", "All activity"],
              ["COMPLETED", "Completed"],
              ["CREATED", "Processing"],
              ["FAILED", "Failed"],
              ["DECLINED", "Declined"],
            ].map(([value, label]) => (
              <button
                key={value}
                className={status === value ? "selected" : ""}
                onClick={() => {
                  setStatus(value);
                  setPage(0);
                }}
              >
                {label}
              </button>
            ))}
          </div>
          <span className="filter-label">
            <SlidersHorizontal size={16} />
            {query.data?.totalElements ?? "—"} transactions
          </span>
        </div>
        {query.isPending ? (
          <Skeleton rows={5} />
        ) : query.error ? (
          <ErrorNotice>{errorMessage(query.error)}</ErrorNotice>
        ) : query.data?.items.length ? (
          <>
            <PaymentTable payments={query.data.items} />
            <Pagination
              page={page}
              totalPages={query.data.totalPages}
              onChange={setPage}
            />
          </>
        ) : (
          <EmptyState
            title="Nothing here just yet."
            message={
              status
                ? "No transactions match this status."
                : "Your transfers and test top-ups will appear here."
            }
          />
        )}
      </section>
    </>
  );
}
