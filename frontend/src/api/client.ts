import type { Problem } from "./types";
let accessToken: string | null = null;
export function setAccessToken(token: string | null) {
  accessToken = token;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public problem: Problem,
  ) {
    super(problem.message);
  }
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(`/api${path}`, { ...init, headers });
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("payflow:unauthorized"));
    const problem =
      typeof body === "object" &&
      body !== null &&
      "code" in body &&
      "message" in body
        ? (body as Problem)
        : {
            code: "NETWORK_ERROR",
            message: "The service is temporarily unavailable. Please retry.",
          };
    throw new ApiError(response.status, problem);
  }
  return body as T;
}
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      UNAUTHORIZED: "Please check your email and password, or sign in again.",
      REQUEST_IN_PROGRESS:
        "Your transfer is still processing. Retry to check the same request.",
      TEST_FUNDING_DISABLED: "Test funding is disabled in this environment.",
      TOPUP_LIMIT_EXCEEDED: "You can add up to ₹10,000 in one test top-up.",
      WALLET_NOT_READY:
        "Your wallet is being prepared. Please try again shortly.",
      RATE_LIMITED:
        "A few too many requests. Please wait a minute and try again.",
    };
    return messages[error.problem.code] ?? error.problem.message;
  }
  return "We could not reach PayFlow. Your existing attempt is safe to retry.";
}
