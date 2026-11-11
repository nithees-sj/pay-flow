import { useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  Clock3,
  AlertCircle,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { useWallet } from "../../api/queries";
import { formatMoney, toMinor, toMajor } from "../../lib/money";
import { reasonMessage } from "../../lib/format";
import { ErrorNotice, Skeleton } from "../../components/Feedback";
import { SendMoneyForm, type SendValues } from "./SendMoneyForm";
import { useTransfer } from "./useTransfer";
export function SendMoneyPage() {
  const { profile } = useAuth();
  const wallet = useWallet();
  const transfer = useTransfer(profile!.user.id);
  const [values, setValues] = useState<SendValues | null>(() =>
    transfer.attempt
      ? {
          toUserEmail: transfer.attempt.body.toUserEmail,
          amount: toMajor(transfer.attempt.body.amount),
          note: transfer.attempt.body.note,
        }
      : null,
  );
  const result = transfer.result;
  function confirm() {
    if (values)
      void transfer.submit({
        toUserEmail: values.toUserEmail,
        amount: toMinor(values.amount),
        currency: "INR",
        note: values.note,
      });
  }
  function again() {
    transfer.reset();
    setValues(null);
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">A LITTLE CLOSER, IN A FEW TAPS</span>
          <h1>Send money</h1>
          <p>For shared lunches, big plans, and everything in between.</p>
        </div>
      </div>
      <div className="send-layout">
        <section className="panel transfer-panel">
          <div className="step-indicator">
            <span className={!values ? "current" : "done"}>
              {values ? <Check size={13} /> : "1"}
            </span>
            Details
            <i />
            <span
              className={values && !result ? "current" : result ? "done" : ""}
            >
              {result ? <Check size={13} /> : "2"}
            </span>
            Review
            <i />
            <span className={result ? "current" : ""}>3</span>Done
          </div>
          {!values && !result ? (
            <>
              <h2>Who are we sending to?</h2>
              <p className="muted">Choose a recipient and make it personal.</p>
              <SendMoneyForm onReview={setValues} />
            </>
          ) : result ? (
            <div className="transfer-result">
              <span className={`result-icon ${result.status.toLowerCase()}`}>
                {result.status === "COMPLETED" ? (
                  <CheckCircle2 size={40} />
                ) : result.status === "CREATED" ? (
                  <Clock3 size={40} />
                ) : (
                  <AlertCircle size={40} />
                )}
              </span>
              <span className="section-kicker">
                {result.status === "COMPLETED"
                  ? "RIGHT WHERE IT NEEDS TO BE"
                  : "YOUR TRANSFER"}
              </span>
              <h2>
                {result.status === "COMPLETED"
                  ? "Money sent."
                  : result.status === "CREATED"
                    ? "We’re checking your transfer."
                    : "This transfer wasn’t completed."}
              </h2>
              <div className="result-amount">{formatMoney(result.amount)}</div>
              <p>
                {result.status === "COMPLETED"
                  ? `Sent to ${values?.toUserEmail ?? transfer.attempt?.body.toUserEmail}`
                  : result.status === "CREATED"
                    ? "You don’t need to send it again. We’ll update this page when the outcome is confirmed."
                    : reasonMessage(result.reasonCode)}
              </p>
              <Link
                className="button button-secondary button-full"
                to={`/transactions/${result.id}`}
              >
                View transfer <ArrowRight size={17} />
              </Link>
              {result.status !== "CREATED" && (
                <button
                  className="button button-primary button-full"
                  onClick={again}
                >
                  Make another transfer
                </button>
              )}
            </div>
          ) : (
            values && (
              <>
                <h2>Looking good?</h2>
                <p className="muted">Take a moment to check the details.</p>
                <div className="review-amount">
                  {formatMoney(toMinor(values.amount))}
                  <span>INR · No transfer fee</span>
                </div>
                <dl className="detail-list">
                  <div>
                    <dt>To</dt>
                    <dd>{values.toUserEmail}</dd>
                  </div>
                  <div>
                    <dt>From</dt>
                    <dd>Your PayFlow wallet</dd>
                  </div>
                  {values.note && (
                    <div>
                      <dt>Note</dt>
                      <dd>{values.note}</dd>
                    </div>
                  )}
                  <div>
                    <dt>Total</dt>
                    <dd>{formatMoney(toMinor(values.amount))}</dd>
                  </div>
                </dl>
                {transfer.error && <ErrorNotice>{transfer.error}</ErrorNotice>}
                <button
                  className="button button-primary button-full"
                  disabled={transfer.busy}
                  onClick={confirm}
                >
                  {transfer.busy
                    ? "Sending securely…"
                    : transfer.attempt
                      ? "Retry this transfer"
                      : "Confirm & send"}
                  <ArrowRight size={18} />
                </button>
                <button
                  className="button button-text button-full"
                  disabled={!!transfer.attempt || transfer.busy}
                  onClick={() => setValues(null)}
                >
                  <ArrowLeft size={16} />
                  Edit details
                </button>
                {transfer.attempt && (
                  <p className="form-footnote">
                    Retries check this same transfer. They never create a second
                    debit.
                  </p>
                )}
              </>
            )
          )}
        </section>
        <aside className="send-aside">
          <div className="panel wallet-summary">
            <span className="summary-icon">
              <Wallet size={22} />
            </span>
            <span className="muted">Available in your wallet</span>
            {wallet.isPending ? (
              <Skeleton rows={1} />
            ) : wallet.isError ? (
              <ErrorNotice>Balance unavailable. Please try again.</ErrorNotice>
            ) : (
              <strong>{formatMoney(wallet.data?.balance ?? "0")}</strong>
            )}
            <span className="currency-pill">INR wallet</span>
          </div>
          <div className="reassurance">
            <ShieldCheck size={26} />
            <h3>A clear trail. Every time.</h3>
            <p>
              Follow every transfer from confirmation to completion, right in
              your transaction history.
            </p>
            <ul>
              <li>
                <Check size={15} />
                No fees in this demo
              </li>
              <li>
                <Check size={15} />
                Protected against duplicate retries
              </li>
              <li>
                <Check size={15} />
                Only simulated funds move
              </li>
            </ul>
          </div>
        </aside>
      </div>
    </>
  );
}
