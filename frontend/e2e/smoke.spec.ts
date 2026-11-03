import { expect, test } from "@playwright/test";

test("dashboard shell loads", async ({ page }) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { level: 1, name: "Air Concepts Financial Admin Dashboard" }),
  ).toBeVisible();
});
