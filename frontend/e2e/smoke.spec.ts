import { expect, test } from "@playwright/test";

// QA-001, AC-6. Opens the app signed out and checks the heading and the login screen.
test("dashboard shell loads and shows the sign-in screen when signed out", async ({ page }) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { level: 1, name: "Air Concepts Financial Admin Dashboard" }),
  ).toBeVisible();
  await expect(page.getByRole("form", { name: "Sign in" })).toBeVisible();
  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(page.getByRole("button", { name: "Send code", exact: true })).toBeVisible();
});
