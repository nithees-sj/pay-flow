export const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
export const shortDate = (value: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(
    new Date(value),
  );
export function reasonMessage(reason: string | null): string {
  const reasons: Record<string, string> = {
    INSUFFICIENT_FUNDS:
      "Your wallet does not have enough funds for this transfer.",
    SELF_TRANSFER: "Choose someone else. You cannot send money to yourself.",
    INVALID_AMOUNT: "The transfer amount must be greater than zero.",
    VELOCITY_LIMIT_EXCEEDED:
      "You have reached the transfer limit for this minute. Try a new transfer later.",
    DAILY_AMOUNT_LIMIT_EXCEEDED: "You have reached your daily transfer limit.",
    RECIPIENT_NOT_FOUND: "No PayFlow wallet was found for that email address.",
    AMOUNT_OVERFLOW: "This amount exceeds the supported wallet balance.",
  };
  return reason
    ? (reasons[reason] ?? "This transfer could not be completed.")
    : "";
}
