import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { api } from "../api/client";
import type { Payment, SendRequest } from "../api/types";
import { useTransfer } from "../features/payments/useTransfer";
vi.mock("../api/client", async () => {
  const actual =
    await vi.importActual<typeof import("../api/client")>("../api/client");
  return { ...actual, api: vi.fn() };
});
const request: SendRequest = {
  toUserEmail: "friend@example.test",
  amount: "100",
  currency: "INR",
  note: "",
};
const payment: Payment = {
  id: "00000000-0000-4000-8000-000000000001",
  transferId: "00000000-0000-4000-8000-000000000002",
  kind: "PAYMENT",
  direction: "OUTGOING",
  senderUserId: "sender",
  recipientUserId: "recipient",
  recipientEmail: request.toUserEmail,
  amount: "100",
  currency: "INR",
  note: "",
  status: "COMPLETED",
  reasonCode: null,
  createdAt: "2026-11-10T10:00:00Z",
  updatedAt: "2026-11-10T10:00:01Z",
  transitions: [],
};
function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      {children}
    </QueryClientProvider>
  );
}
beforeEach(() => {
  sessionStorage.clear();
  vi.mocked(api).mockReset();
});
it("reuses the same key and original body after a lost response", async () => {
  let posts = 0;
  vi.mocked(api).mockImplementation(async (_path, init) => {
    if (init?.method === "POST" && posts++ === 0)
      throw new TypeError("Network error");
    return payment;
  });
  const { result } = renderHook(() => useTransfer("user-one"), { wrapper });
  await act(() => result.current.submit(request));
  expect(result.current.error).toBeTruthy();
  await act(() => result.current.submit({ ...request, amount: "200" }));
  const calls = vi
    .mocked(api)
    .mock.calls.filter((call) => call[1]?.method === "POST");
  expect(calls).toHaveLength(2);
  expect(calls[0][1]?.headers).toEqual(calls[1][1]?.headers);
  expect(calls[1][1]?.body).toBe(JSON.stringify(request));
  await waitFor(() =>
    expect(sessionStorage.getItem("payflow-attempt:user-one")).toBeNull(),
  );
});
it("restores an unresolved key after navigating away", async () => {
  vi.mocked(api).mockRejectedValue(new TypeError("Network error"));
  const first = renderHook(() => useTransfer("user-two"), { wrapper });
  await act(() => first.result.current.submit(request));
  const key = first.result.current.attempt?.key;
  first.unmount();
  const restored = renderHook(() => useTransfer("user-two"), { wrapper });
  expect(restored.result.current.attempt?.key).toBe(key);
  await act(() => restored.result.current.submit(request));
  const calls = vi
    .mocked(api)
    .mock.calls.filter((call) => call[1]?.method === "POST");
  expect(calls[0][1]?.headers).toEqual(calls[1][1]?.headers);
});
it("checks the existing payment after a processing response", async () => {
  vi.mocked(api).mockImplementation(async (_path, init) =>
    init?.method === "POST" ? { ...payment, status: "CREATED" } : payment,
  );
  const { result } = renderHook(() => useTransfer("user-three"), { wrapper });
  await act(() => result.current.submit(request));
  await waitFor(() => expect(result.current.result?.status).toBe("COMPLETED"));
  expect(
    vi.mocked(api).mock.calls.filter((call) => call[1]?.method === "POST"),
  ).toHaveLength(1);
  expect(
    vi
      .mocked(api)
      .mock.calls.some(
        (call) => call[0] === `/payments/${payment.id}` && !call[1]?.method,
      ),
  ).toBe(true);
});
