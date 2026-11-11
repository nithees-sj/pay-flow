import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { CheckCircle2, AlertCircle, Inbox } from "lucide-react";
const ToastContext = createContext<(message: string, error?: boolean) => void>(
  () => undefined,
);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<
    { id: string; message: string; error: boolean }[]
  >([]);
  const notify = useCallback((message: string, error = false) => {
    const id = crypto.randomUUID();
    setItems((old) => [...old.slice(-2), { id, message, error }]);
    window.setTimeout(
      () => setItems((old) => old.filter((t) => t.id !== id)),
      5000,
    );
  }, []);
  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toasts" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.error ? "toast-error" : ""}`}>
            {t.error ? <AlertCircle size={19} /> : <CheckCircle2 size={19} />}
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
export const useToast = () => useContext(ToastContext);
export function ErrorNotice({ children }: { children: ReactNode }) {
  return (
    <div className="error-notice" role="alert">
      <AlertCircle size={18} />
      <span>{children}</span>
    </div>
  );
}
export function EmptyState({
  title,
  message,
  action,
}: {
  title: string;
  message: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Inbox size={28} />
      </span>
      <h3>{title}</h3>
      <p>{message}</p>
      {action}
    </div>
  );
}
export function Skeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="skeleton-group" aria-label="Loading" role="status">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" />
      ))}
    </div>
  );
}
