import { describe, expect, it } from "vitest";
import { formatMoney, formatDate, dateRange, formatDecimal } from "./format";

describe("UC5 shared financial and calendar formatting", () => {
  it("FR-UC15-06 preserves large monetary strings, cents, currencies and negative fractions", () => {
    expect(formatMoney("9007199254740993.01", "NZD")).toContain("9,007,199,254,740,993.01");
    expect(formatMoney("1.20", "AUD")).toContain("AUD");
    expect(formatMoney("-0.05", "NZD")).toMatch(/^-NZD/);
    expect(formatMoney("-0.05", "NZD")).toContain("0.05");
    expect(formatMoney("1.2345", "NZD")).toContain("1.2345");
    expect(formatMoney("1.2E+3", "NZD")).toContain("1,200.00");
  });
  it("FR-UC15-06 distinguishes missing, invalid, confirmed zero and absent currency", () => {
    expect(formatMoney(null, "NZD")).toBe("Not recorded");
    expect(formatMoney(undefined, "NZD")).toBe("Not recorded");
    expect(formatMoney("invalid", "NZD")).toBe("Unavailable");
    expect(formatMoney("0", "NZD")).toContain("0.00");
    expect(formatMoney("2", undefined)).toBe("2.00 (currency not recorded)");
    expect(formatDecimal("2.1234")).toBe("2.1234");
  });
  it("FR-UC15-27 preserves date-only values without a timezone shift and rejects invalid dates", () => {
    expect(formatDate("2026-11-04")).toBe("4 Nov 2026");
    expect(formatDate("2026-02-31")).toBe("Unavailable");
    expect(formatDate(null)).toBe("Not recorded");
  });
  it("FR-UC15-27 uses Auckland calendar boundaries for all presets", () => {
    const now = new Date("2026-12-31T11:30:00Z");
    expect(dateRange("ALL_DATES", now)).toEqual({});
    expect(dateRange("THIS_MONTH", now)).toEqual({ from: "2027-01-01", to: "2027-01-31" });
    expect(dateRange("LAST_THREE_MONTHS", now)).toEqual({ from: "2026-11-01", to: "2027-01-31" });
    expect(dateRange("THIS_YEAR", now)).toEqual({ from: "2027-01-01", to: "2027-12-31" });
    expect(dateRange("THIS_MONTH", new Date("2028-02-15T00:00:00Z"))).toEqual({
      from: "2028-02-01",
      to: "2028-02-29",
    });
  });
});
