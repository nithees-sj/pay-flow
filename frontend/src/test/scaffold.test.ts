import { test, expect } from "vitest";
test("supports exact minor-unit arithmetic", () => {
  expect(BigInt("9007199254740993") + 1n).toBe(9007199254740994n);
});
