import { expect, test } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { greenLane, signIn, ENTITY_A } from "./support/green-lane";
import { forecastBoundary } from "./support/forecast";
test("FR-UC04-05 forecast totals have a distinct background across each row", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  const grid = page.getByRole("region", { name: "Cash forecast table" });
  const ordinary = grid
    .getByRole("row", { name: /Staffing Expenses/ })
    .locator("td")
    .first();
  const ordinaryBackground = await ordinary.evaluate((cell) => {
    let element: Element | null = cell;
    while (element) {
      const background = getComputedStyle(element).backgroundColor;
      if (background !== "rgba(0, 0, 0, 0)") return background;
      element = element.parentElement;
    }
    return "transparent";
  });
  for (const label of ["Total Money In", "Total Money Out", "Closing balance"]) {
    const row = grid
      .getByRole("row")
      .filter({ has: page.getByRole("rowheader", { name: label, exact: true }) });
    const cells = row.locator("th, td");
    const backgrounds = await cells.evaluateAll((elements) =>
      elements.map((cell) => getComputedStyle(cell).backgroundColor),
    );
    expect(backgrounds.length).toBeGreaterThan(1);
    expect(backgrounds[0]).not.toBe(ordinaryBackground);
    expect(backgrounds.every((background) => background === backgrounds[0])).toBe(true);
  }
});
test("FR-UC04-04 grid opening edit refreshes the saved amount", async ({ page }) => {
  await greenLane(page, "FINANCIAL_ADMIN");
  const state = await forecastBoundary(page);
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  const grid = page.getByRole("region", { name: "Cash forecast table" });
  const openingValue = grid.getByRole("button", { name: /^Edit opening balance for / }).first();
  const openingRow = openingValue.locator("xpath=ancestor::tr");
  const rowHeight = (await openingRow.boundingBox())?.height;
  await expect(openingValue).not.toHaveText("Edit");
  expect((await openingValue.boundingBox())?.height).toBeLessThan(32);
  await openingValue.focus();
  await expect(openingValue).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const input = grid.getByRole("textbox", { name: /^Opening balance for / });
  await input.fill("500.00");
  const save = grid.getByRole("button", { name: "Save", exact: true });
  expect((await openingRow.boundingBox())?.height).toBe(rowHeight);
  expect((await save.boundingBox())?.width).toBeLessThan(32);
  expect((await save.boundingBox())?.height).toBeLessThan(32);
  await input.press("Enter");
  await expect.poll(() => state.opening.get(ENTITY_A)).toBe("500.00");
  await expect(grid.getByText(/500\.00/).first()).toBeVisible();
  await expect(grid.getByText(/615\.00/).first()).toBeVisible();
});
test("FR-UC04-04 compact editor keeps a failed value visible and cancellable", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  await page.route("**/opening-balance", async (route) => {
    if (route.request().method() === "PUT")
      await route.fulfill({ status: 500, json: { code: "FAILED" } });
    else await route.fallback();
  });
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  const grid = page.getByRole("region", { name: "Cash forecast table" });
  const value = grid.getByRole("button", { name: /^Edit opening balance for / }).first();
  await value.click();
  const input = grid.getByRole("textbox", { name: /^Opening balance for / });
  await input.fill("500.00");
  await grid.getByRole("button", { name: "Save", exact: true }).click();
  const error = grid.getByRole("alert");
  await expect(error).toBeVisible();
  await expect(error).toBeInViewport();
  await expect(input).toHaveValue("500.00");
  await expect(input).toBeFocused();
  await input.press("Escape");
  await expect(value).toBeFocused();
});
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
    await expect(page.getByRole("region", { name: "Cash forecast table" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Create forecast" })).toBeVisible();
    const grid = page.getByRole("region", { name: "Cash forecast table" });
    await grid
      .getByRole("button", { name: /^Edit opening balance for / })
      .first()
      .click();
    const editor = grid.getByRole("textbox", { name: /^Opening balance for / });
    await expect(editor).toBeVisible();
    await expect(grid.getByRole("button", { name: "Cancel", exact: true })).toBeVisible();
    await expect(editor).toBeInViewport();
    await expect(grid.getByRole("button", { name: "Cancel", exact: true })).toBeInViewport();
    await editor.fill("1000.00");
    await editor.press("Enter");
    await expect.poll(() => state.opening.get(ENTITY_A)).toBe("1000.00");
    await expect(page.getByText("Financial year summary", { exact: true })).toHaveCount(0);
    await expect(grid).toBeVisible();
    await expect(page.getByText("Monthly forecast actions", { exact: true })).toHaveCount(0);
    await expect(page.getByText("How this is calculated", { exact: true })).toHaveCount(0);
    for (const name of ["Forecast", "Xero items", "Manual items", "Staffing", "History"]) {
      await expect(page.getByRole("tab", { name, exact: true })).toBeVisible();
    }
    // Exclusion is an invoice-level action on the Xero items tab.
    await page
      .getByRole("tablist", { name: "Forecast sections" })
      .getByRole("tab", { name: "Xero items", exact: true })
      .click();
    await page.getByRole("button", { name: "Exclude", exact: true }).click();
    await page.getByLabel("Reason for exclusion").fill("No cash expected in this period");
    await page.getByRole("button", { name: "Exclude from forecast", exact: true }).click();
    await expect.poll(() => state.excluded.get(ENTITY_A)).toBe("No cash expected in this period");
    await expect(page.getByText("Excluded: No cash expected in this period")).toBeVisible();
    await expect(page.getByRole("button", { name: "Include", exact: true })).toBeVisible();
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
  await expect(page.getByRole("region", { name: "Cash forecast table" })).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.getByRole("group", { name: "Financial year" })).toBeVisible();
});
test("FR-UC04-03_13 manual and staffing edits, deactivation and entity selection", async ({
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
    if (kind === "manual") {
      await page.getByLabel("Cash amount (NZD)", { exact: true }).fill("100.00");
    } else {
      await page.getByLabel("Hourly rate (NZD)", { exact: true }).fill("100.00");
      await page.getByLabel("Standard hours per week", { exact: true }).fill("40");
      await page.getByLabel("First scheduled payday", { exact: true }).fill("2026-11-06");
    }
    await page.getByRole("button", { name: "Save item", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: `Synthetic ${kind}`, exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: `Edit Synthetic ${kind}`, exact: true }).click();
    await page
      .getByLabel(kind === "manual" ? "Cash amount (NZD)" : "Hourly rate (NZD)", {
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
  await expect(page.getByText("Select an entity to view its forecast.")).toBeVisible();
  await expect(page.getByText(/Incomplete totals:/)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Add staffing line", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Export to Excel", exact: true })).toHaveCount(0);
});
