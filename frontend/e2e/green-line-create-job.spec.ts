import { expect, test } from "@playwright/test";
import { ENTITY_A, JOB_A, greenLane, signIn } from "./support/green-lane";

for (const width of [390, 1440]) {
  test(`GL-13 create a mock job at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await greenLane(page, "DIRECTOR");
    let created = false;
    let submitted: unknown;
    const summary = () => ({
      id: JOB_A,
      entityId: ENTITY_A,
      entityName: "First entity",
      xeroQuoteId: "00000000-0000-4000-8000-000000000120",
      quoteNumber: "Q-A",
      jobId: "J-A",
      customerName: "Customer A",
      xeroQuoteStatus: "ACCEPTED",
      stage: "TO_SCHEDULE",
      stageEnteredAt: "2026-11-19T00:00:00Z",
      daysInStage: 2,
      late: false,
      nextAction: created ? "SCHEDULE_IN_VWORK" : "CREATE_JOB",
      flags: [],
      attention: [],
      version: created ? 1 : 0,
    });
    await page.route("**/api/v1/work-items/my-work**", async (route) => {
      if (route.request().method() === "OPTIONS") return route.fallback();
      await route.fulfill({
        headers: { "Access-Control-Allow-Origin": "*" },
        json: {
          needsAction: [summary()],
          waiting: [],
          needsAttention: [],
          generatedAt: "2026-11-19T00:00:00Z",
          truncated: false,
        },
      });
    });
    await page.route(`**/api/v1/entities/${ENTITY_A}/work-items/${JOB_A}**`, async (route) => {
      if (route.request().method() === "OPTIONS") return route.fallback();
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith("/job-draft"))
        return route.fulfill({
          headers: { "Access-Control-Allow-Origin": "*" },
          json: {
            entityId: ENTITY_A,
            itemId: JOB_A,
            version: 0,
            reference: "J-A",
            quoteNumber: "Q-A",
            customer: "Customer A",
            contactPerson: "Alex",
            phone: "555123",
            scope: [{ description: "Install", quantity: "1" }],
            templates: [{ templateRef: "mock-template", isDefault: true }],
            defaultTemplate: "mock-template",
            missingFields: ["siteAddress"],
          },
        });
      if (path.endsWith("/vwork-job")) {
        submitted = route.request().postDataJSON();
        created = true;
        return route.fulfill({
          headers: { "Access-Control-Allow-Origin": "*" },
          json: {
            operationId: "00000000-0000-4000-8000-000000000199",
            outcome: "CREATED",
          },
        });
      }
      return route.fulfill({
        headers: { "Access-Control-Allow-Origin": "*" },
        json: {
          summary: summary(),
          vwork: created ? { jobId: "mock-000001" } : {},
          invoice: {},
          links: {},
          events: [],
          operations: [],
          allowedActions: created ? [] : ["CREATE_JOB"],
        },
      });
    });
    await signIn(page, "mywork");
    await page.getByRole("button", { name: "Create job Q-A" }).click();
    await page.getByRole("button", { name: "Create vWork job" }).click();
    await expect(page.getByRole("button", { name: "Create job", exact: true })).toBeDisabled();
    await page.getByLabel("Site address").fill("Example Street");
    await page.getByRole("button", { name: "Create job", exact: true }).click();
    await expect(page.getByText("vWork job linked.")).toBeVisible();
    await expect(page.getByRole("button", { name: "Create vWork job" })).toHaveCount(0);
    expect(submitted).toMatchObject({
      reference: "J-A",
      siteAddress: "Example Street",
      templateId: "mock-template",
    });
    expect(JSON.stringify(submitted)).not.toMatch(/unitAmount|lineAmount|tax|discount/);
  });
}
