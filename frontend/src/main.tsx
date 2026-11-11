import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./features/auth/AuthProvider";
import { ToastProvider } from "./components/Feedback";
import { App } from "./app/App";
import { ApiError } from "./api/client";
import "./styles.css";
if (localStorage.getItem("payflow-theme") === "dark")
  document.documentElement.classList.add("dark");
const client = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5000,
      retry: (count, error) =>
        !(error instanceof ApiError && error.status < 500) && count < 1,
    },
  },
});
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <a href="#main-content" className="skip-link">
              Skip to content
            </a>
            <App />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
