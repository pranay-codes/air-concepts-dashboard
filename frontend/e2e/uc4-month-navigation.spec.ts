import { expect, test } from "@playwright/test";
import { greenLane, signIn } from "./support/green-lane";
import { forecastBoundary } from "./support/forecast";

test("FR-UC04-05 navigates directly to a month on a narrow screen", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  const picker = page.getByLabel("Go to month");
  await picker.fill("2024-11");
  await expect(
    page
      .getByRole("region", { name: "Cash forecast table" })
      .getByRole("columnheader", { name: "November 2024" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Next month" }).click();
  await expect(picker).toHaveValue("2024-12");
  await expect(
    page
      .getByRole("region", { name: "Cash forecast table" })
      .getByRole("columnheader", { name: "December 2024" }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("FR-UC04-05 month controls are compact, aligned and keyboard accessible", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await greenLane(page, "FINANCIAL_ADMIN");
  await forecastBoundary(page);
  await signIn(page);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Forecast", exact: true })
    .click();
  const previous = page.getByRole("button", { name: "Previous month" });
  const next = page.getByRole("button", { name: "Next month" });
  const picker = page.getByLabel("Go to month");
  const previousBox = await previous.boundingBox();
  const nextBox = await next.boundingBox();
  const pickerBox = await picker.boundingBox();
  expect(previousBox).not.toBeNull();
  expect(nextBox).not.toBeNull();
  expect(pickerBox).not.toBeNull();
  expect(previousBox!.height).toBe(36);
  expect(nextBox!.height).toBe(previousBox!.height);
  expect(pickerBox!.height).toBe(previousBox!.height);
  expect(Math.abs(previousBox!.y - pickerBox!.y)).toBeLessThanOrEqual(1);
  await previous.focus();
  await expect(previous).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(next).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(picker).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath("month-controls.png") });
});
