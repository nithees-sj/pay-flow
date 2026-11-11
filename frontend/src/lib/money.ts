const LONG_MAX = 9223372036854775807n;
/** Converts rupee input to paise without floating-point arithmetic. */
export function toMinor(input: string): string {
  if (!/^(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?$/.test(input))
    throw new Error("Enter a valid amount with up to two decimal places.");
  const [whole, fraction = ""] = input.split(".");
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  if (minor <= 0n || minor > LONG_MAX)
    throw new Error("Enter a positive amount within the supported range.");
  return minor.toString();
}
export function toMajor(minor: string): string {
  const value = BigInt(minor);
  return `${value / 100n}.${(value % 100n).toString().padStart(2, "0")}`;
}
export function formatMoney(minor: string, currency = "INR"): string {
  const value = BigInt(minor);
  const absolute = value < 0n ? -value : value;
  const whole = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(absolute / 100n);
  return `${value < 0n ? "−" : ""}${whole}.${(absolute % 100n).toString().padStart(2, "0")}`;
}
