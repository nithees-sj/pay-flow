export interface User {
  id: string;
  email: string;
  name: string;
}
export interface Profile {
  user: User;
  wallet: { id: string; currency: string; status: "ACTIVE" | "PROVISIONING" };
}
export interface Token {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}
export interface Wallet {
  accountId: string;
  currency: string;
  balance: string;
  updatedAt: string;
}
export interface Entry {
  id: string;
  transferId: string;
  direction: "DEBIT" | "CREDIT";
  amount: string;
  currency: string;
  createdAt: string;
}
export type PaymentStatus = "CREATED" | "COMPLETED" | "FAILED" | "DECLINED";
export interface Transition {
  fromStatus: PaymentStatus | null;
  toStatus: PaymentStatus;
  reasonCode: string | null;
  occurredAt: string;
}
export interface Payment {
  id: string;
  transferId: string;
  kind: "PAYMENT" | "TEST_TOPUP";
  direction: "INCOMING" | "OUTGOING";
  senderUserId: string | null;
  recipientUserId: string | null;
  recipientEmail: string | null;
  amount: string;
  currency: string;
  note: string;
  status: PaymentStatus;
  reasonCode: string | null;
  createdAt: string;
  updatedAt: string;
  transitions: Transition[];
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Notification {
  id: string;
  paymentId: string;
  type: string;
  messageKey: string;
  messageParameters: { amount: string; currency: string; direction: string };
  createdAt: string;
  readAt: string | null;
}
export interface Inbox extends Page<Notification> {
  unreadCount: number;
}
export interface SendRequest {
  toUserEmail: string;
  amount: string;
  currency: "INR";
  note: string;
}
export interface Problem {
  code: string;
  message: string;
  traceId?: string;
  status?: number;
}
