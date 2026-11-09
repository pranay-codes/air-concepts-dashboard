import { expect, test } from "@playwright/test";
import { greenLane, signIn, ENTITY_A, ENTITY_B, STAFF_A } from "./support/green-lane";

test("FR-UC15-05_07_11_30 sales details, confirmed assignment and entity isolation", async ({
  page,
}) => {
  const state = await greenLane(page);
  await signIn(page);
  const owner = page.getByLabel("Owner for INV-001", { exact: true }).filter({ visible: true });
  await expect(owner).toBeEnabled();
  await owner.selectOption(STAFF_A);
  await expect(owner).toHaveValue(STAFF_A);
  expect(state.owners.get(ENTITY_A)).toBe(STAFF_A);
  await page
    .getByRole("button", { name: "INV-001", exact: true })
    .filter({ visible: true })
    .click();
  await expect(page.getByRole("heading", { name: "Invoice INV-001", exact: true })).toBeVisible();
  await expect(page.getByText("Service work", { exact: true })).toBeVisible();
  await expect(page.getByText("Bank payment")).toBeVisible();
  await page.getByRole("button", { name: "Refresh details" }).click();
  await expect(page.getByText("Service work", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Back to invoices" }).click();
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeFocused();
  await expect(owner).toHaveValue(STAFF_A);
  await page.getByLabel("Entity", { exact: true }).selectOption(ENTITY_B);
  await expect(
    page.getByText("Second entity customer", { exact: true }).filter({ visible: true }),
  ).toBeVisible();
  await expect(owner).toHaveValue("");
  await page.getByLabel("Entity", { exact: true }).selectOption(ENTITY_A);
  await expect(owner).toHaveValue(STAFF_A);
  await page.reload();
  await expect(owner).toHaveValue(STAFF_A);
  await owner.selectOption("");
  await expect(owner).toHaveValue("");
  expect(state.owners.has(ENTITY_A)).toBe(false);
});

test("FR-UC15-08_14 bounded pagination and provider failure recovery", async ({ page }) => {
  const state = await greenLane(page);
  state.invoiceCount = 26;
  await signIn(page);
  await expect(page.getByText("Page 1 of 2", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "INV-026", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toHaveCount(0);
  await expect(page.getByText("Page 2 of 2", { exact: true })).toBeVisible();
  await page.getByLabel("Search invoices").fill("INV-001");
  await expect(page.getByText("Page 1 of 1", { exact: true })).toBeVisible();
  state.failInvoices = true;
  await page.getByRole("button", { name: "Refresh invoices", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText(
    "Xero is unavailable. Retry when the connection is restored.",
  );
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toHaveCount(0);
  state.failInvoices = false;
  await page.getByRole("button", { name: "Retry invoices", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  expect(
    state.requests.some((request) => new URLSearchParams(request.query).get("page") === "2"),
  ).toBe(true);
});

test("FR-UC15-31 mobile detail returns keyboard focus to its invoice", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await greenLane(page);
  await signIn(page);
  const trigger = page
    .getByRole("button", { name: "INV-001", exact: true })
    .filter({ visible: true });
  await trigger.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "Invoice INV-001", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Invoice INV-001", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Back to invoices", exact: true }).click();
  await expect(trigger).toBeFocused();
});

test("FR-UC15-15_21_26 search recovery and bills without assignment", async ({ page }) => {
  const state = await greenLane(page);
  await signIn(page);
  await page.getByLabel("Search invoices", { exact: true }).fill("no matching contact");
  await expect(page.getByText("INV-001", { exact: true }).filter({ visible: true })).toHaveCount(0);
  await expect
    .poll(() =>
      state.requests.some(
        (r) => new URLSearchParams(r.query).get("search") === "no matching contact",
      ),
    )
    .toBe(true);
  await page.getByRole("button", { name: "Clear filters", exact: true }).first().click();
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "Supplier bills", exact: true }).click();
  await expect(page.getByRole("table", { name: "Supplier bills", exact: true })).toBeVisible();
  await expect(page.getByLabel("Owner for INV-001", { exact: true })).toHaveCount(0);
});

test("FR-UC15-01_16 Director entity, Xero callback and user administration", async ({ page }) => {
  const state = await greenLane(page);
  await signIn(page);
  await page.getByRole("button", { name: "Settings", exact: true }).click();
  await page.getByRole("button", { name: "Add entity", exact: true }).click();
  await page.getByLabel("Entity name", { exact: true }).fill("Fourth entity");
  await page.getByRole("button", { name: "Continue to Xero", exact: true }).click();
  // The synthetic Xero redirect returns with the attempt, which opens the organisation choice.
  const dialog = page.getByRole("dialog", { name: /Connect Fourth entity/ });
  await dialog.getByLabel("Xero organisation", { exact: true }).selectOption({
    label: "Synthetic Xero organisation",
  });
  await dialog.getByRole("button", { name: "Continue", exact: true }).click();
  await dialog.getByRole("button", { name: "Confirm connection", exact: true }).click();
  await expect(
    page.getByText("Connected Fourth entity to Synthetic Xero organisation."),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "Fourth entity", exact: true })).toBeVisible();
  expect(state.confirmed).toBe(true);
  await page.getByRole("button", { name: "Users", exact: true }).click();
  await page.getByLabel("User email").fill("new@example.invalid");
  await page.getByLabel("User name").fill("New staff");
  await page.getByLabel("User role").selectOption("STANDARD_USER");
  await page.getByRole("button", { name: "Register user", exact: true }).click();
  await page.getByRole("button", { name: "Edit New staff", exact: true }).click();
  await page.getByLabel("Account active").uncheck();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Save user changes", exact: true }).click();
  await expect
    .poll(() => state.users.find((u) => u.email === "new@example.invalid")?.active)
    .toBe(false);
});

test("FR-UC15-21_32 Standard user has no bill, administration or assignment controls", async ({
  page,
}) => {
  const state = await greenLane(page, "STANDARD_USER");
  await signIn(page);
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  await expect(page.getByRole("tab", { name: "Supplier bills" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Settings", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Owner for INV-001")).toHaveCount(0);
  expect(
    state.requests.some(
      (r) => r.path.endsWith("/users") || r.path.endsWith("/staff") || r.query.includes("ACCPAY"),
    ),
  ).toBe(false);
});

test("FR-UC15-07_16_21 Financial Admin can assign and read bills without Director settings", async ({
  page,
}) => {
  const state = await greenLane(page, "FINANCIAL_ADMIN");
  await signIn(page);
  await expect(
    page.getByLabel("Owner for INV-001", { exact: true }).filter({ visible: true }),
  ).toBeEnabled();
  await expect(page.getByRole("button", { name: "Settings", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "Supplier bills", exact: true }).click();
  await expect(page.getByRole("table", { name: "Supplier bills", exact: true })).toBeVisible();
  expect(state.requests.some((request) => request.path.includes("/users"))).toBe(false);
});

test("FR-UC15-18_21 a denied bill read clears revoked controls and revalidates the role", async ({
  page,
}) => {
  const state = await greenLane(page);
  await signIn(page);
  await page.getByRole("tab", { name: "Supplier bills", exact: true }).click();
  await expect(page.getByRole("table", { name: "Supplier bills", exact: true })).toBeVisible();
  state.role = "STANDARD_USER";
  await page.getByRole("button", { name: "Refresh invoices", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Supplier bills", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Settings", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Owner for INV-001", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Sales invoices", exact: true })).toBeVisible();
});

for (const width of [320, 390, 768, 1024, 1440]) {
  test(`FR-UC15-31 workspace reflows at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await greenLane(page);
    await signIn(page);
    await page.getByLabel("Entity", { exact: true }).selectOption(ENTITY_B);
    await expect(
      page.getByText("Second entity customer", { exact: true }).filter({ visible: true }),
    ).toBeVisible();
    await page.screenshot({ path: `../output/playwright/workspace-${width}.png`, fullPage: true });
    const overflow = await page.evaluate(() =>
      [...document.querySelectorAll("body *")]
        .filter((element) => {
          const box = element.getBoundingClientRect();
          return (
            box.width > 0 &&
            box.right > window.innerWidth &&
            !element.closest(".invoice-table-wrap, .detail-table-wrap")
          );
        })
        .map((element) => `${element.tagName}.${element.className}`),
    );
    expect(overflow).toEqual([]);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
  });
}
