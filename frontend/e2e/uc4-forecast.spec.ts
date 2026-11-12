import { expect, test } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { greenLane, signIn, ENTITY_A } from "./support/green-lane";
import { forecastBoundary } from "./support/forecast";
const fixture = fileURLToPath(
  new URL("../../backend/src/test/resources/forecast/template-v1.xlsx", import.meta.url),
);
for (const width of [320, 390, 768, 1024, 1440]) {
  test(`FR-UC04-05_07_15_19 Chromium forecast journey at ${width}px`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await greenLane(page, "FINANCIAL_ADMIN");
    const state = await forecastBoundary(page);
    await signIn(page);
    await page
      .getByRole("navigation", { name: "Main navigation" })
      .getByRole("button", { name: "Forecast", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Financial forecast", exact: true }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: /^Edit opening balance for / })
      .filter({ visible: true })
      .first()
      .click();
    await page.getByLabel("Opening cash balance (NZD)").fill("1000.00");
    await page.getByLabel("Balance source (optional)").fill("Synthetic statement");
    await page.getByRole("button", { name: "Save opening balance", exact: true }).click();
    await expect(
      page
        .getByText(/1,115\.00/)
        .filter({ visible: true })
        .first(),
    ).toBeVisible();
    expect(state.opening.get(ENTITY_A)).toBe("1000.00");
    await expect(
      page.getByRole("button", { name: /^View / }).filter({ visible: true }),
    ).toHaveCount(12);
    const monthTrigger = page
      .getByRole("button", { name: /^View / })
      .filter({ visible: true })
      .first();
    await monthTrigger.click();
    // The month opens as a full page at every width: the table is replaced by the ledger.
    await expect(page.getByRole("heading", { name: /cash forecast$/ })).toBeVisible();
    await expect(
      page.getByRole("button", { name: /^View / }).filter({ visible: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("region", { name: "Money In" }).getByText(/INV-001 · Forecast customer/),
    ).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath(`uc4-detail-${width}.png`), fullPage: true });
    await page.getByRole("button", { name: "Back to forecast", exact: true }).click();
    await expect(monthTrigger).toBeFocused();
    // Exclusion is an invoice-level action on the Xero items tab.
    await page
      .getByRole("tablist", { name: "Forecast sections" })
      .getByRole("tab", { name: "Xero items", exact: true })
      .click();
    await page.getByRole("button", { name: "Exclude", exact: true }).click();
    await page.getByLabel("Reason for exclusion").fill("No cash expected in this period");
    await page.getByRole("button", { name: "Exclude from forecast", exact: true }).click();
    await expect.poll(() => state.excluded.get(ENTITY_A)).toBe("No cash expected in this period");
    await page
      .getByRole("tablist", { name: "Forecast sections" })
      .getByRole("tab", { name: "Forecast", exact: true })
      .click();
    await expect(page.getByText(/1,115\.00/).filter({ visible: true })).toHaveCount(0);
    await page.getByText("Excluded documents (1)", { exact: true }).click();
    await expect(page.getByRole("button", { name: "Include again", exact: true })).toHaveCount(1);
    let downloads = 0;
    page.on("download", () => downloads++);
    await page.getByRole("button", { name: "Export to Excel", exact: true }).click();
    await page.getByLabel("Forecast template (.xlsx)").setInputFiles(fixture);
    await page.getByRole("button", { name: "Generate workbook", exact: true }).click();
    await expect(page.getByRole("button", { name: "Download", exact: true })).toBeVisible();
    expect(downloads).toBe(0);
    expect(state.exports).toBe(1);
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download", exact: true }).click();
    expect((await download).suggestedFilename()).toMatch(
      /^forecast-synthetic-\d{4}-\d{2}-20261110-1000\.xlsx$/,
    );
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await expect(page.getByRole("main")).toHaveCount(1);
    await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
    await page.screenshot({ path: testInfo.outputPath(`uc4-${width}.png`), fullPage: true });
  });
}
test("FR-UC04-07 STANDARD_USER has no Forecast destination", async ({ page }) => {
  await greenLane(page, "STANDARD_USER");
  await signIn(page);
  await expect(page.getByRole("button", { name: "Forecast", exact: true })).toHaveCount(0);
});
test("FR-UC04-06_07 failed forecast load retains context and Retry recovers", async ({ page }) => {
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  let first = true;
  await page.route(/\/entities\/[^/]+\/forecast(?:\?|$)/, async (route) => {
    if (first) {
      first = false;
      await route.fulfill({
        status: 500,
        json: { code: "FORECAST_SYNC_FAILED", message: "Synthetic failure" },
      });
    } else await route.fallback();
  });
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Financial forecast", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByRole("button", { name: /^View / }).filter({ visible: true })).toHaveCount(
    12,
  );
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.getByRole("group", { name: "Financial year" })).toBeVisible();
});
test("FR-UC04-03_13 manual and staffing edits, deactivation and combined view", async ({
  page,
}) => {
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  for (const kind of ["manual", "staffing"] as const) {
    const tab = kind === "manual" ? "Manual items" : "Staffing";
    await page
      .getByRole("tablist", { name: "Forecast sections" })
      .getByRole("tab", { name: tab, exact: true })
      .click();
    await page
      .getByRole("button", {
        name: kind === "manual" ? "Add manual item" : "Add staffing line",
        exact: true,
      })
      .click();
    await page
      .getByLabel(kind === "manual" ? "Description" : "Staffing label", { exact: true })
      .fill(`Synthetic ${kind}`);
    await page
      .getByLabel(kind === "manual" ? "Cash amount (NZD)" : "Monthly cash cost (NZD)", {
        exact: true,
      })
      .fill("100.00");
    await page.getByRole("button", { name: "Save item", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: `Synthetic ${kind}`, exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: `Edit Synthetic ${kind}`, exact: true }).click();
    await page
      .getByLabel(kind === "manual" ? "Cash amount (NZD)" : "Monthly cash cost (NZD)", {
        exact: true,
      })
      .fill("125.00");
    await page.getByRole("button", { name: "Save item", exact: true }).click();
    await expect(page.getByText(/125\.00/)).toBeVisible();
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: `Deactivate Synthetic ${kind}`, exact: true }).click();
    await expect(page.getByRole("heading", { name: `Synthetic ${kind}`, exact: true })).toHaveCount(
      0,
    );
    await page.getByLabel("Show inactive", { exact: true }).check();
    await expect(
      page.getByRole("heading", { name: `Synthetic ${kind}`, exact: true }),
    ).toBeVisible();
  }
  await page.locator("#entity-select").selectOption("__all__");
  await expect(page.getByRole("heading", { name: "All entities", exact: true })).toBeVisible();
  await expect(page.getByText(/Incomplete totals:/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Add staffing line", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Export to Excel", exact: true })).toHaveCount(0);
});
