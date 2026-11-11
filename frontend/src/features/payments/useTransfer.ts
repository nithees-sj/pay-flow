import { useState, useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { api, errorMessage } from "../../api/client";
import type { Payment, SendRequest } from "../../api/types";
const attemptSchema = z.object({
  key: z.string().uuid(),
  body: z.object({
    toUserEmail: z.string(),
    amount: z.string(),
    currency: z.literal("INR"),
    note: z.string(),
  }),
  paymentId: z.string().uuid().optional(),
});
type Attempt = z.infer<typeof attemptSchema>;
function read(storageKey: string): Attempt | null {
  try {
    const value = attemptSchema.safeParse(
      JSON.parse(sessionStorage.getItem(storageKey) ?? "null"),
    );
    return value.success ? value.data : null;
  } catch {
    return null;
  }
}
export function useTransfer(userId: string) {
  const storageKey = `payflow-attempt:${userId}`;
  const [attempt, setAttempt] = useState<Attempt | null>(() =>
    read(storageKey),
  );
  const [response, setResponse] = useState<Payment | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const cache = useQueryClient();
  const current = useQuery({
    queryKey: ["payment", attempt?.paymentId],
    queryFn: () => api<Payment>(`/payments/${attempt!.paymentId}`),
    enabled: !!attempt?.paymentId,
    refetchInterval: (query) =>
      query.state.data?.status === "CREATED" || !query.state.data
        ? 2000
        : false,
  });
  const result = current.data ?? response;
  useEffect(() => {
    if (result && result.status !== "CREATED") {
      sessionStorage.removeItem(storageKey);
      void cache.invalidateQueries({ queryKey: ["wallet"] });
      void cache.invalidateQueries({ queryKey: ["payments"] });
    }
  }, [result, storageKey, cache]);
  async function submit(body: SendRequest) {
    if (busy) return;
    setBusy(true);
    setError("");
    const next = attempt ?? { key: crypto.randomUUID(), body };
    setAttempt(next);
    sessionStorage.setItem(storageKey, JSON.stringify(next));
    try {
      const payment = await api<Payment>("/payments", {
        method: "POST",
        headers: { "Idempotency-Key": next.key },
        body: JSON.stringify(next.body),
      });
      const accepted = { ...next, paymentId: payment.id };
      setAttempt(accepted);
      sessionStorage.setItem(storageKey, JSON.stringify(accepted));
      setResponse(payment);
      if (payment.status !== "CREATED") sessionStorage.removeItem(storageKey);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  function reset() {
    if (result?.status === "CREATED" || busy) return;
    sessionStorage.removeItem(storageKey);
    setAttempt(null);
    setResponse(null);
    setError("");
  }
  return {
    attempt,
    result,
    busy,
    error: error || (current.error ? errorMessage(current.error) : ""),
    submit,
    reset,
  };
}
