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
