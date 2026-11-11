import { describe, expect, it } from "vitest";
import { formatMoney, toMinor } from "../lib/money";

describe("exact money", () => {
  it("parses decimal rupees without binary rounding", () => {
    expect(toMinor("0.29")).toBe("29");
    expect(toMinor("123.4")).toBe("12340");
  });
  it("preserves amounts beyond JavaScript safe integers", () => {
    expect(toMinor("90071992547409.93")).toBe("9007199254740993");
    expect(formatMoney("9007199254740993")).toBe("₹9,00,71,99,25,47,409.93");
  });
  it("accepts the Java long maximum and rejects overflow", () => {
    expect(toMinor("92233720368547758.07")).toBe("9223372036854775807");
    expect(() => toMinor("92233720368547758.08")).toThrow();
  });
  it.each(["0", "-1", "1.001", "1e3", "01", "", "NaN"])(
    "rejects %s",
    (value) => {
      expect(() => toMinor(value)).toThrow();
    },
  );
  it("formats zero and signed system balances", () => {
    expect(formatMoney("0")).toBe("₹0.00");
    expect(formatMoney("-12345")).toBe("−₹123.45");
  });
});
