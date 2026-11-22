import { expect, test } from "@playwright/test";
import { ENTITY_A, ENTITY_B, greenLane, signIn } from "./support/green-lane";

for (const width of [390, 1440]) {
  test(`GL-10 My Work entity and detail journey at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const state = await greenLane(page, "STANDARD_USER");
    await signIn(page, "mywork");
    await expect(page.getByRole("button", { name: "Follow up Q-A" })).toBeVisible();
    await expect(page.getByText("Xero has not synced yet.")).toBeVisible();
    await page.getByRole("button", { name: "Refresh sync" }).click();
    await expect
      .poll(() =>
        state.requests.some(
          (request) =>
            request.path === `/api/v1/entities/${ENTITY_A}/green-line/sync` &&
            request.method === "POST" &&
            request.authorized,
        ),
      )
      .toBe(true);
    await page.getByLabel("Entity", { exact: true }).selectOption("__all__");
    await expect(page.getByRole("button", { name: "Follow up Q-B" })).toBeVisible();
    await page.getByRole("button", { name: "Follow up Q-B" }).click();
    await expect(page.getByLabel("Entity", { exact: true })).toHaveValue(ENTITY_B);
    await expect(page.getByRole("heading", { name: "Q-B", exact: true })).toBeVisible();
    await expect(page.getByLabel("Follow-up date")).toBeFocused();
    await page.getByRole("button", { name: "Back to My Work" }).click();
    await expect(page.getByLabel("Entity", { exact: true })).toHaveValue("__all__");
    await expect(page.getByRole("button", { name: "Follow up Q-B" })).toBeFocused();
    expect(
      state.requests
        .filter((request) => request.path.includes("work-items"))
        .every((request) => request.authorized),
    ).toBe(true);
    expect(
      state.requests.some(
        (request) => new URLSearchParams(request.query).get("entityId") === ENTITY_A,
      ),
    ).toBe(true);
  });
}
