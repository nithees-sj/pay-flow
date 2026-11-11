import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowRight, Mail, IndianRupee } from "lucide-react";
import { toMinor } from "../../lib/money";
const schema = z.object({
  toUserEmail: z
    .string()
    .trim()
    .email("Enter the recipient’s email address.")
    .max(254),
  amount: z.string().refine((value) => {
    try {
      toMinor(value);
      return true;
    } catch {
      return false;
    }
  }, "Enter an amount greater than zero, with up to two decimal places."),
  note: z.string().max(280, "Keep your note under 280 characters."),
});
export type SendValues = z.infer<typeof schema>;
export function SendMoneyForm({
  initial,
  onReview,
}: {
  initial?: SendValues;
  onReview: (values: SendValues) => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SendValues>({
    resolver: zodResolver(schema),
    defaultValues: initial ?? { toUserEmail: "", amount: "", note: "" },
  });
  return (
    <form onSubmit={handleSubmit(onReview)} noValidate>
      <div className="field">
        <label htmlFor="recipient">Recipient’s email</label>
        <div className="input-icon">
          <Mail size={18} />
          <input
            id="recipient"
            type="email"
            placeholder="friend@example.com"
            autoComplete="off"
            {...register("toUserEmail")}
            aria-invalid={!!errors.toUserEmail}
          />
        </div>
        {errors.toUserEmail && (
          <span className="field-error">{errors.toUserEmail.message}</span>
        )}
        <small>They’ll need an active PayFlow wallet.</small>
      </div>
      <div className="field">
        <label htmlFor="amount">Amount</label>
        <div className="input-icon amount-field">
          <IndianRupee size={21} />
          <input
            id="amount"
            inputMode="decimal"
            placeholder="0.00"
            {...register("amount")}
            aria-invalid={!!errors.amount}
          />
          <span className="input-currency">INR</span>
        </div>
        {errors.amount && (
          <span className="field-error">{errors.amount.message}</span>
        )}
      </div>
      <div className="field">
        <label htmlFor="note">
          Add a note <span>Optional</span>
        </label>
        <textarea
          id="note"
          rows={3}
          maxLength={280}
          placeholder="What’s it for?"
          {...register("note")}
        />
        {errors.note && (
          <span className="field-error">{errors.note.message}</span>
        )}
      </div>
      <button className="button button-primary button-full">
        Review transfer <ArrowRight size={18} />
      </button>
      <p className="form-footnote">
        You’ll confirm the details before any money moves.
      </p>
    </form>
  );
}
