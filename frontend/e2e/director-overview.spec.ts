import { expect, test } from "@playwright/test";
import { ENTITY_A, ENTITY_B, greenLane, signIn } from "./support/green-lane";

for (const width of [320, 390, 768, 1024, 1440]) {
  test(`GL-24 Director overview across entities and for one entity at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    const state = await greenLane(page, "DIRECTOR");
    await signIn(page, "mywork");
    await page.getByRole("button", { name: "Overview", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Overview", level: 1 })).toBeVisible();
    await page.getByLabel("Entity", { exact: true }).selectOption("__all__");
    const decisions = page.getByRole("region", { name: "Needs your decision" });
    await decisions.getByRole("link", { name: "See uninvoiced jobs" }).click();
    const waiting = page.getByRole("region", { name: "Completed, not invoiced" });
    await expect(waiting).toBeFocused();
    await waiting.getByRole("button", { name: "Open Q-B" }).click();
    await expect(page.getByRole("heading", { name: "Q-B", level: 1 })).toBeVisible();
    await expect(page.getByLabel("Entity", { exact: true })).toHaveValue(ENTITY_B);
    await page.getByRole("button", { name: "Back to Overview" }).click();
    await expect(page.getByLabel("Entity", { exact: true })).toHaveValue("__all__");
    await expect(waiting).toBeFocused();
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

test("GL-24 Overview keeps usable targets and reflows with doubled text", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await greenLane(page, "DIRECTOR");
  await signIn(page, "mywork");
  await page.getByRole("button", { name: "Overview", exact: true }).click();
  await expect(page.getByRole("region", { name: "Needs your decision" })).toBeVisible();
  await page.getByText("Change period").click();
  await page
    .getByText(/: \d+ open/)
    .first()
    .click();
  const targets = page.getByRole("main").locator("a, button, summary").filter({ visible: true });
  for (const target of await targets.all()) {
    const box = (await target.boundingBox())!;
    expect(Math.min(box.width, box.height), await target.innerText()).toBeGreaterThanOrEqual(24);
  }
  // A wide font as well, so a machine with a narrow default font still catches overflow.
  await page.addStyleTag({
    content: "html { font-size: 200%; } :root { --font-body: Verdana, 'DejaVu Sans', sans-serif; }",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("GL-DIR-06 Financial Admin has no Overview navigation", async ({ page }) => {
  await greenLane(page, "FINANCIAL_ADMIN");
  await signIn(page, "mywork");
  await expect(page.getByRole("button", { name: "Overview", exact: true })).toHaveCount(0);
});
