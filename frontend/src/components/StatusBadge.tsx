import type { PaymentStatus } from "../api/types";
export function StatusBadge({ status }: { status: PaymentStatus }) {
  const labels = {
    CREATED: "Processing",
    COMPLETED: "Completed",
    FAILED: "Failed",
    DECLINED: "Declined",
  };
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <span />
      {labels[status]}
    </span>
  );
}
