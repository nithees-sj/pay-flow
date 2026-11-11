import { useQuery } from "@tanstack/react-query";
import { api } from "./client";
import type { Wallet, Page, Payment, Entry, Inbox } from "./types";
export const useWallet = () =>
  useQuery({
    queryKey: ["wallet"],
    queryFn: () => api<Wallet>("/wallet"),
    refetchInterval: 10000,
  });
export const usePayments = (page = 0, status = "", size = 10) =>
  useQuery({
    queryKey: ["payments", page, status, size],
    queryFn: () =>
      api<Page<Payment>>(
        `/payments?page=${page}&size=${size}${status ? `&status=${status}` : ""}`,
      ),
    refetchInterval: 10000,
  });
export const useEntries = () =>
  useQuery({
    queryKey: ["entries"],
    queryFn: () => api<Page<Entry>>("/wallet/entries?size=100"),
    refetchInterval: 10000,
  });
export const useInbox = (page = 0, unread = false, size = 20) =>
  useQuery({
    queryKey: ["notifications", page, unread, size],
    queryFn: () =>
      api<Inbox>(`/notifications?page=${page}&size=${size}&unread=${unread}`),
    refetchInterval: 5000,
  });
