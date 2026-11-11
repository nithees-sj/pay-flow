import { useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowRight,
  Plus,
  Eye,
  EyeOff,
  Wallet,
  ShieldCheck,
  CalendarDays,
  Send,
} from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { useWallet, usePayments, useEntries } from "../../api/queries";
import { errorMessage } from "../../api/client";
import { formatMoney } from "../../lib/money";
import { ErrorNotice, EmptyState, Skeleton } from "../../components/Feedback";
import { PaymentTable } from "../payments/PaymentTable";
import { TopupDialog } from "../payments/TopupDialog";
import { ActivityChart } from "./ActivityChart";
export function DashboardPage() {
  const { profile } = useAuth();
  const wallet = useWallet();
  const payments = usePayments(0, "", 5);
  const entries = useEntries();
  const [showBalance, setShowBalance] = useState(true);
  const [funding, setFunding] = useState(false);
  const credit = (entries.data?.items ?? [])
    .filter((e) => e.direction === "CREDIT")
    .reduce((sum, e) => sum + BigInt(e.amount), 0n);
  const debit = (entries.data?.items ?? [])
    .filter((e) => e.direction === "DEBIT")
    .reduce((sum, e) => sum + BigInt(e.amount), 0n);
  const contacts = [
    ...new Map(
      (payments.data?.items ?? [])
        .filter((p) => p.recipientEmail && p.direction === "OUTGOING")
        .map((p) => [p.recipientEmail, p]),
    ).values(),
  ].slice(0, 3);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">YOUR MONEY, AT A GLANCE</span>
          <h1>
            Welcome back, {profile?.user.name.split(" ")[0]}{" "}
            <span className="welcome-dot">✦</span>
          </h1>
          <p>A clear view of where you are, and where you’re going.</p>
        </div>
        <span className="date-pill">
          <CalendarDays size={16} />
          {new Intl.DateTimeFormat("en-IN", {
            day: "numeric",
            month: "short",
            year: "numeric",
          }).format(new Date())}
        </span>
      </div>
      {profile?.wallet.status === "PROVISIONING" && (
        <div className="info-banner">
          Your wallet is being prepared. This page will update as soon as it’s
          ready.
        </div>
      )}
      <div className="dashboard-top">
        <section className="balance-card">
          <div className="balance-top">
            <span>
              <Wallet size={19} />
              Available balance
            </span>
            <button
              className="balance-visibility"
              onClick={() => setShowBalance(!showBalance)}
              aria-label={showBalance ? "Hide balance" : "Show balance"}
            >
              {showBalance ? <Eye size={18} /> : <EyeOff size={18} />}
            </button>
          </div>
          {wallet.isPending ? (
            <div className="balance-loading">Loading your wallet…</div>
          ) : wallet.error ? (
            <p className="balance-error">{errorMessage(wallet.error)}</p>
          ) : (
            <div className="balance-amount">
              {showBalance
                ? formatMoney(wallet.data?.balance ?? "0")
                : "₹ ••••••"}
              <span>INR</span>
            </div>
          )}
          <div className="wallet-identity">
            <span className="wallet-live-dot" />
            Personal wallet
            <span>•••• {profile?.wallet.id.slice(-4).toUpperCase()}</span>
          </div>
          <div className="balance-actions">
            <Link className="button button-white" to="/send">
              <ArrowUpRight size={19} />
              Send money
            </Link>
            <button
              className="button button-glass"
              onClick={() => setFunding(true)}
            >
              <Plus size={18} />
              Add test funds
            </button>
          </div>
          <div className="balance-decoration" aria-hidden="true" />
        </section>
        <section className="panel quick-send">
          <div className="panel-heading">
            <h2>Make someone’s day</h2>
            <Send size={19} />
          </div>
          <p>
            Good things are better shared.
            <br />
            Send a little something their way.
          </p>
          <div className="contact-row">
            {contacts.map((p) => (
              <Link
                to="/send"
                className="contact"
                key={p.recipientEmail}
                title={p.recipientEmail!}
              >
                <span className="contact-avatar">
                  {p.recipientEmail!.slice(0, 2).toUpperCase()}
                </span>
                <span>{p.recipientEmail!.split("@")[0].slice(0, 10)}</span>
              </Link>
            ))}
            <Link to="/send" className="contact">
              <span className="contact-avatar contact-add">
                <Plus size={24} />
              </span>
              <span>Someone new</span>
            </Link>
          </div>
          <Link className="text-link" to="/send">
            Start a transfer <ArrowRight size={16} />
          </Link>
        </section>
      </div>
      <div className="dashboard-middle">
        <section className="panel activity-panel">
          <div className="panel-heading">
            <div>
              <h2>Money in motion</h2>
              <p className="muted">Last 7 days · latest 100 wallet entries</p>
            </div>
            <div className="chart-legend">
              <span>
                <i />
                In
              </span>
              <span>
                <i />
                Out
              </span>
            </div>
          </div>
          {entries.isPending ? (
            <Skeleton rows={3} />
          ) : entries.error ? (
            <ErrorNotice>{errorMessage(entries.error)}</ErrorNotice>
          ) : (
            <ActivityChart entries={entries.data?.items ?? []} />
          )}
        </section>
        <section className="movement-summary">
          <div className="panel movement-card">
            <span className="movement-icon incoming">
              <ArrowDownLeft size={22} />
            </span>
            <div>
              <span className="muted">Recent money in</span>
              <strong>{formatMoney(credit.toString())}</strong>
              <small>Includes test funds</small>
            </div>
          </div>
          <div className="panel movement-card">
            <span className="movement-icon outgoing">
              <ArrowUpRight size={22} />
            </span>
            <div>
              <span className="muted">Recent money out</span>
              <strong>{formatMoney(debit.toString())}</strong>
              <small>From latest 100 entries</small>
            </div>
          </div>
        </section>
      </div>
      <section className="panel transactions-panel">
        <div className="panel-heading">
          <div>
            <h2>Recent activity</h2>
            <p className="muted">The little details, all in one place.</p>
          </div>
          <Link className="text-link" to="/transactions">
            View all <ArrowRight size={16} />
          </Link>
        </div>
        {payments.isPending ? (
          <Skeleton />
        ) : payments.error ? (
          <ErrorNotice>{errorMessage(payments.error)}</ErrorNotice>
        ) : payments.data?.items.length ? (
          <PaymentTable payments={payments.data.items} />
        ) : (
          <EmptyState
            title="Your first move starts here."
            message="Add some test funds, then send your first payment."
            action={
              <button
                className="button button-secondary"
                onClick={() => setFunding(true)}
              >
                <Plus size={17} />
                Add test funds
              </button>
            }
          />
        )}
      </section>
      <div className="dashboard-note">
        <ShieldCheck size={16} />
        Every movement has a clear record. You’re always in the loop.
      </div>
      {funding && <TopupDialog onClose={() => setFunding(false)} />}
    </>
  );
}
