/** Display helpers only: authoritative financial values and overdue calculations stay on the server. */
type Decimal = { negative: boolean; whole: string; fraction: string };

function decimal(value: string): Decimal | null {
  if (value.length > 256) return null;
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(value);
  if (!match) return null;
  const whole = match[2] ?? "0";
  const fraction = match[3] ?? "";
  const exponent = Number(match[4] ?? "0"); // Bounded position only; the amount never becomes a Number.
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 100) return null;
  const digits = whole + fraction;
  const point = whole.length + exponent;
  const integer =
    point <= 0 ? "0" : digits.slice(0, point) + "0".repeat(Math.max(0, point - digits.length));
  const decimals = point <= 0 ? "0".repeat(-point) + digits : digits.slice(point);
  return {
    negative: match[1] === "-",
    whole: integer.replace(/^0+(?=\d)/, ""),
    fraction: decimals.replace(/0+$/, ""),
  };
}

function sign(value: Decimal) {
  return value.negative && /[1-9]/.test(value.whole + value.fraction) ? "-" : "";
}

const integerFormat = new Intl.NumberFormat("en-NZ", { maximumFractionDigits: 0 });
const currencyFormats = new Map<string, Intl.NumberFormat>();

export function formatDecimal(value: string | null | undefined, minimumFractionDigits = 0): string {
  if (value === null || value === undefined) return "Not recorded";
  const parsed = decimal(value);
  if (!parsed) return "Unavailable";
  const fraction = parsed.fraction.padEnd(minimumFractionDigits, "0");
  return (
    sign(parsed) + integerFormat.format(BigInt(parsed.whole)) + (fraction ? `.${fraction}` : "")
  );
}

export function formatMoney(
  value: string | null | undefined,
  currency: string | null | undefined,
): string {
  if (value === null || value === undefined) return "Not recorded";
  const parsed = decimal(value);
  if (!parsed) return "Unavailable";
  if (!currency) return `${formatDecimal(value, 2)} (currency not recorded)`;
  if (!/^[A-Z]{3}$/.test(currency)) return "Unavailable";
  let formatter = currencyFormats.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-NZ", {
      style: "currency",
      currency,
      currencyDisplay: "code",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    currencyFormats.set(currency, formatter);
  }
  return (
    sign(parsed) +
    formatter
      .formatToParts(BigInt(parsed.whole))
      .map((part) => (part.type === "fraction" ? parsed.fraction.padEnd(2, "0") : part.value))
      .join("")
  );
}

const invoiceDateFormat = new Intl.DateTimeFormat("en-NZ", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});
export function formatDate(value: string | null | undefined): string {
  if (value === null || value === undefined) return "Not recorded";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "Unavailable";
  const date = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value)
    return "Unavailable";
  return invoiceDateFormat.format(date);
}

const fetchTimeFormat = new Intl.DateTimeFormat("en-NZ", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  timeZone: "Pacific/Auckland",
  timeZoneName: "short",
});
export function formatFetchTime(value: string | Date | null | undefined): string {
  if (!value) return "Fetch time unknown";
  const date = value instanceof Date ? value : new Date(value);
  return Number.isFinite(date.getTime())
    ? `${fetchTimeFormat.format(date)} (Pacific/Auckland)`
    : "Fetch time unknown";
}

export type DatePreset = "ALL_DATES" | "THIS_MONTH" | "LAST_THREE_MONTHS" | "THIS_YEAR" | "CUSTOM";
const businessDateFormat = new Intl.DateTimeFormat("en-NZ", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  timeZone: "Pacific/Auckland",
});
export function dateRange(preset: DatePreset, now = new Date()): { from?: string; to?: string } {
  if (preset === "ALL_DATES" || preset === "CUSTOM") return {};
  const parts = businessDateFormat.formatToParts(now);
  const year = Number(parts.find((part) => part.type === "year")?.value);
  const month = Number(parts.find((part) => part.type === "month")?.value) - 1;
  const calendar = (y: number, m: number, day: number) => {
    const date = new Date(0);
    date.setUTCFullYear(y, m, day);
    return date.toISOString().slice(0, 10);
  };
  if (preset === "THIS_YEAR") return { from: calendar(year, 0, 1), to: calendar(year, 11, 31) };
  return {
    from: calendar(year, preset === "LAST_THREE_MONTHS" ? month - 2 : month, 1),
    to: calendar(year, month + 1, 0),
  };
}
