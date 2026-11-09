import { expect, test } from "@playwright/test";
import { greenLane, signIn } from "./support/green-lane";

test("FR-UC15-17 OTP reaches the workspace and sign-out clears it", async ({ page }) => {
  const state = await greenLane(page);
  await signIn(page);
  await expect(page.getByText("Alex Example", { exact: true }).first()).toBeVisible();
  expect(state.otpBody).toMatchObject({ email: "alex@example.invalid", create_user: false });
  expect(state.requests.filter((r) => r.path.startsWith("/api/")).every((r) => r.authorized)).toBe(
    true,
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByLabel("Email", { exact: true })).toBeVisible();
  await expect(page.getByText("INV-001", { exact: true })).toHaveCount(0);
});

test("unauthenticated request to a running local API is rejected", async ({ request }) => {
  test.skip(
    process.env.E2E_LOCAL_API !== "true",
    "Opt in with E2E_LOCAL_API=true when the local API is running.",
  );
  const port = Number(process.env.E2E_LOCAL_API_PORT ?? "8080");
  expect(Number.isInteger(port) && port > 0 && port <= 65535).toBe(true);
  const response = await request.get(`http://127.0.0.1:${port}/api/v1/me`);

  expect(response.status()).toBe(401);
});
