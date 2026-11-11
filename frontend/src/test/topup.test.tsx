import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { TopupDialog } from "../features/payments/TopupDialog";
vi.mock("../api/client", async () => ({
  ...(await vi.importActual<typeof import("../api/client")>("../api/client")),
  api: vi.fn(),
}));
vi.mock("../features/auth/AuthProvider", () => ({
  useAuth: () => ({ profile: { user: { id: "test-user" } } }),
}));
vi.mock("../components/Feedback", async () => ({
  ...(await vi.importActual<typeof import("../components/Feedback")>(
    "../components/Feedback",
  )),
  useToast: () => vi.fn(),
}));
beforeEach(() => {
  sessionStorage.clear();
  vi.mocked(api).mockReset();
  HTMLDialogElement.prototype.showModal = vi.fn(function (
    this: HTMLDialogElement,
  ) {
    this.open = true;
  });
});
function renderDialog() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <TopupDialog onClose={vi.fn()} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("starts a new attempt after a definitive failure", async () => {
  vi.mocked(api).mockResolvedValue({ status: "FAILED" });
  renderDialog();
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /Add test funds/ }));
  await screen.findByText("This top-up could not be completed.");
  expect(screen.getByLabelText("Amount in INR")).toBeEnabled();
  await user.click(screen.getByRole("button", { name: /Add test funds/ }));
  await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api).mock.calls[0][1]?.headers).not.toEqual(
    vi.mocked(api).mock.calls[1][1]?.headers,
  );
});
it("retries an uncertain result with the same key and amount", async () => {
  vi.mocked(api).mockRejectedValue(new TypeError("Network error"));
  renderDialog();
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /Add test funds/ }));
  await user.click(
    await screen.findByRole("button", { name: /Retry this top-up/ }),
  );
  await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
  expect(screen.getByLabelText("Amount in INR")).toBeDisabled();
  expect(vi.mocked(api).mock.calls[0][1]).toEqual(
    vi.mocked(api).mock.calls[1][1],
  );
});
