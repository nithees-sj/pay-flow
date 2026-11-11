import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SendMoneyForm } from "../features/payments/SendMoneyForm";

describe("send form", () => {
  it("validates recipient and positive amount before review", async () => {
    const review = vi.fn();
    render(<SendMoneyForm onReview={review} />);
    await userEvent.click(
      screen.getByRole("button", { name: /review transfer/i }),
    );
    expect(
      await screen.findByText("Enter the recipient’s email address."),
    ).toBeVisible();
    expect(screen.getByText(/amount greater than zero/i)).toBeVisible();
    expect(review).not.toHaveBeenCalled();
  });
  it("reviews exact decimal input and note", async () => {
    const review = vi.fn();
    render(<SendMoneyForm onReview={review} />);
    fireEvent.change(screen.getByLabelText("Recipient’s email"), {
      target: { value: "friend@example.test" },
    });
    fireEvent.change(screen.getByLabelText("Amount"), {
      target: { value: "125.50" },
    });
    fireEvent.change(screen.getByLabelText(/Add a note/), {
      target: { value: "Lunch" },
    });
    await userEvent.click(
      screen.getByRole("button", { name: /review transfer/i }),
    );
    await waitFor(() => expect(review).toHaveBeenCalled());
    expect(review.mock.calls[0][0]).toEqual({
      toUserEmail: "friend@example.test",
      amount: "125.50",
      note: "Lunch",
    });
  });
  it("rejects fractional paise", async () => {
    render(
      <SendMoneyForm
        onReview={vi.fn()}
        initial={{
          toUserEmail: "friend@example.test",
          amount: "0.001",
          note: "",
        }}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: /review transfer/i }),
    );
    expect(await screen.findByText(/amount greater than zero/i)).toBeVisible();
  });
});
