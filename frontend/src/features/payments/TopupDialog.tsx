import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { X, Plus, FlaskConical } from "lucide-react";
import { api, errorMessage } from "../../api/client";
import type { Payment } from "../../api/types";
import { toMinor } from "../../lib/money";
import { ErrorNotice, useToast } from "../../components/Feedback";
import { useAuth } from "../auth/AuthProvider";
interface Attempt {
  key: string;
  amount: string;
  paymentId?: string;
}
function saved(key: string): Attempt | null {
  try {
    const v: unknown = JSON.parse(sessionStorage.getItem(key) ?? "null");
    if (
      v &&
      typeof v === "object" &&
      "key" in v &&
      typeof v.key === "string" &&
      "amount" in v &&
      typeof v.amount === "string"
    )
      return v as Attempt;
  } catch {
    /* Start a new local attempt. */
  }
  return null;
}
export function TopupDialog({ onClose }: { onClose: () => void }) {
  const { profile } = useAuth();
  const storage = `payflow-topup:${profile!.user.id}`;
  const dialog = useRef<HTMLDialogElement>(null);
  const attempt = useRef<Attempt | null>(saved(storage));
  const [amount, setAmount] = useState(attempt.current?.amount ?? "1000");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [paymentId, setPaymentId] = useState(attempt.current?.paymentId);
  const cache = useQueryClient();
  const toast = useToast();
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  useEffect(() => {
    if (!paymentId) return;
    let active = true;
    const check = () => {
      api<Payment>(`/payments/${paymentId}`)
        .then((p) => {
          if (active && p.status !== "CREATED") {
            sessionStorage.removeItem(storage);
            attempt.current = null;
            setPaymentId(undefined);
            void cache.invalidateQueries({ queryKey: ["wallet"] });
            toast(
              p.status === "COMPLETED"
                ? "Test funds are in your wallet."
                : "The test top-up did not complete.",
              p.status !== "COMPLETED",
            );
          }
        })
        .catch(() => undefined);
    };
    check();
    const interval = window.setInterval(check, 2000);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [paymentId, storage, cache, toast]);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    let minor: string;
    try {
      minor = toMinor(amount);
      if (BigInt(minor) > 1000000n)
        throw new Error("Add up to ₹10,000 at a time.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Enter a valid amount.");
      return;
    }
    setBusy(true);
    attempt.current ??= { key: crypto.randomUUID(), amount };
    sessionStorage.setItem(storage, JSON.stringify(attempt.current));
    try {
      const result = await api<Payment>("/wallet/topups", {
        method: "POST",
        headers: { "Idempotency-Key": attempt.current.key },
        body: JSON.stringify({ amount: minor, currency: "INR" }),
      });
      if (result.status === "CREATED") {
        attempt.current.paymentId = result.id;
        sessionStorage.setItem(storage, JSON.stringify(attempt.current));
        setPaymentId(result.id);
      } else {
        sessionStorage.removeItem(storage);
        attempt.current = null;
        if (result.status === "COMPLETED") {
          await cache.invalidateQueries();
          toast("Test funds added. You’re ready to send.");
          onClose();
        } else setError("This top-up could not be completed.");
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <dialog
      className="modal"
      ref={dialog}
      onCancel={(event) => {
        if (busy) event.preventDefault();
        else onClose();
      }}
      onClose={onClose}
    >
      <div className="modal-heading">
        <span className="summary-icon">
          <FlaskConical size={24} />
        </span>
        <button
          className="icon-button"
          aria-label="Close top-up"
          disabled={busy}
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </div>
      <h2>A little fuel for your wallet.</h2>
      <p className="muted">
        Add simulated INR to explore PayFlow. No bank account or card needed.
      </p>
      {paymentId ? (
        <>
          <p>Your top-up is processing. We’re checking its existing status.</p>
          <Link
            className="button button-primary button-full"
            to={`/transactions/${paymentId}`}
            onClick={onClose}
          >
            View pending top-up
          </Link>
        </>
      ) : (
        <form onSubmit={submit}>
          <div className="amount-presets">
            {["500", "1000", "5000"].map((value) => (
              <button
                type="button"
                key={value}
                disabled={!!attempt.current}
                className={amount === value ? "selected" : ""}
                onClick={() => setAmount(value)}
              >
                ₹{value}
              </button>
            ))}
          </div>
          <div className="field">
            <label htmlFor="topup-amount">Amount in INR</label>
            <input
              id="topup-amount"
              inputMode="decimal"
              value={amount}
              disabled={!!attempt.current}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
          {error && <ErrorNotice>{error}</ErrorNotice>}
          <button className="button button-primary button-full" disabled={busy}>
            <Plus size={18} />
            {busy
              ? "Adding funds…"
              : attempt.current
                ? "Retry this top-up"
                : "Add test funds"}
          </button>
          <p className="form-footnote">
            Up to ₹10,000 per top-up. Test money only.
          </p>
        </form>
      )}
    </dialog>
  );
}
