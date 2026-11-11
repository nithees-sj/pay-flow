import { useEffect } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { AppShell } from "./AppShell";
import { AuthPage } from "../features/auth/AuthPage";
import { RequireAuth } from "../features/auth/AuthProvider";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { SendMoneyPage } from "../features/payments/SendMoneyPage";
import { TransactionsPage } from "../features/payments/TransactionsPage";
import { PaymentDetailPage } from "../features/payments/PaymentDetailPage";
import { NotificationsPage } from "../features/notifications/NotificationsPage";
export function App() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return (
    <Routes>
      <Route path="/login" element={<AuthPage key="login" mode="login" />} />
      <Route
        path="/register"
        element={<AuthPage key="register" mode="register" />}
      />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="send" element={<SendMoneyPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="transactions/:id" element={<PaymentDetailPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
