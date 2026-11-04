import { expect, test } from "@playwright/test";

// Requirement R8. Needs the synthetic Test user and the local API on port 8080.
// Credentials come from the environment and are never committed.
const email = process.env.E2E_TEST_EMAIL;
const password = process.env.E2E_TEST_PASSWORD;

test("signed-in user reaches the protected API and sees their profile", async ({ page }) => {
  test.skip(!email || !password, "Set E2E_TEST_EMAIL and E2E_TEST_PASSWORD to run this journey.");

  await page.goto("/");
  await page.getByLabel("Email").fill(email!);
  await page.getByLabel("Password").fill(password!);
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page.getByText(/^Signed in as [0-9a-f-]{36}$/)).toBeVisible();
});

test("unauthenticated request to the protected API is rejected", async ({ request }) => {
  const response = await request.get("http://localhost:8080/api/v1/me");

  expect(response.status()).toBe(401);
});
