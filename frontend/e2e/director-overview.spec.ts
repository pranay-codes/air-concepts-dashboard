import { expect, test } from "@playwright/test";
import { ENTITY_A, greenLane, signIn } from "./support/green-lane";

for (const width of [390, 1440]) {
  test(`GL-24 Director overview across entities and for one entity at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    const state = await greenLane(page, "DIRECTOR");
    await signIn(page, "mywork");
    await page.getByRole("button", { name: "Overview", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Overview", level: 1 })).toBeVisible();
    await page.getByLabel("Entity", { exact: true }).selectOption("__all__");
    const people = page.getByRole("region", { name: "Who has what" });
    await people.getByText("Unassigned: 2 open").click();
    await expect(people.getByText("Q-B").filter({ visible: true })).toBeVisible();
    await expect(
      page.getByRole("region", { name: "Jobs completed" }).getByText("Taylor Technician"),
    ).toBeVisible();
    await page.getByLabel("Entity", { exact: true }).selectOption(ENTITY_A);
    await expect(people.getByText("Unassigned: 1 open")).toBeVisible();
    await expect(page.getByText("Q-B")).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      state.requests
        .filter((request) => request.path.endsWith("/overview"))
        .every((request) => request.authorized),
    ).toBe(true);
  });
}

test("GL-DIR-06 Financial Admin has no Overview navigation", async ({ page }) => {
  await greenLane(page, "FINANCIAL_ADMIN");
  await signIn(page, "mywork");
  await expect(page.getByRole("button", { name: "Overview", exact: true })).toHaveCount(0);
});
