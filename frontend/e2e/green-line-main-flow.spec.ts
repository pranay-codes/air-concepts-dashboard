import { expect, test } from "@playwright/test";
import { ENTITY_A, JOB_A, greenLane, signIn } from "./support/green-lane";

for (const width of [390, 1440]) {
  test(`GL main flow from quote to test invoice at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await greenLane(page, "DIRECTOR");
    let stage: "QUOTED" | "TO_SCHEDULE" | "SCHEDULED" | "TO_INVOICE" | "INVOICE_DRAFTED" = "QUOTED";
    let invoiceSubmitted: unknown;
    const summary = () => ({
      id: JOB_A,
      entityId: ENTITY_A,
      entityName: "First entity",
      xeroQuoteId: "00000000-0000-4000-8000-000000000120",
      quoteNumber: "Q-A",
      jobId: "J-A",
      customerName: "Customer A",
      xeroQuoteStatus: stage === "QUOTED" ? "SENT" : "ACCEPTED",
      quoteTotal: "115.00",
      currency: "NZD",
      stage,
      stageEnteredAt: "2026-11-19T00:00:00Z",
      daysInStage: 1,
      late: false,
      nextAction: {
        QUOTED: "FOLLOW_UP",
        TO_SCHEDULE: "CREATE_JOB",
        SCHEDULED: "AWAIT_COMPLETION",
        TO_INVOICE: "PREPARE_INVOICE",
        INVOICE_DRAFTED: "REVIEW_AND_SEND",
      }[stage],
      flags: [],
      attention: [],
      version: { QUOTED: 0, TO_SCHEDULE: 1, SCHEDULED: 2, TO_INVOICE: 3, INVOICE_DRAFTED: 4 }[
        stage
      ],
    });
    await page.route(`**/api/v1/entities/${ENTITY_A}/green-line/sync`, async (route) => {
      if (route.request().method() === "OPTIONS") return route.fallback();
      if (route.request().method() === "POST") {
        if (stage === "QUOTED") stage = "TO_SCHEDULE";
        else if (stage === "SCHEDULED") stage = "TO_INVOICE";
      }
      await route.fulfill({
        headers: { "Access-Control-Allow-Origin": "*" },
        json: { entityId: ENTITY_A, xero: {}, vwork: {} },
      });
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
      const headers = { "Access-Control-Allow-Origin": "*" };
      if (path.endsWith("/job-draft"))
        return route.fulfill({
          headers,
          json: {
            entityId: ENTITY_A,
            itemId: JOB_A,
            version: 1,
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
        stage = "SCHEDULED";
        return route.fulfill({
          headers,
          json: { operationId: "00000000-0000-4000-8000-000000000199", outcome: "CREATED" },
        });
      }
      if (path.endsWith("/invoice-draft"))
        return route.fulfill({
          headers,
          json: {
            entityId: ENTITY_A,
            itemId: JOB_A,
            version: 3,
            quoteNumber: "Q-A",
            organisationName: "Synthetic Xero organisation",
            customer: "Customer A",
            reference: "J-A / Q-A",
            lineCount: 1,
            total: "115.00",
            currency: "NZD",
            fingerprint: "a".repeat(64),
          },
        });
      if (path.endsWith("/invoice")) {
        invoiceSubmitted = route.request().postDataJSON();
        stage = "INVOICE_DRAFTED";
        return route.fulfill({
          headers,
          json: { operationId: "00000000-0000-4000-8000-000000000198", outcome: "CREATED" },
        });
      }
      return route.fulfill({
        headers,
        json: {
          summary: summary(),
          vwork: stage === "QUOTED" || stage === "TO_SCHEDULE" ? {} : { jobId: "mock-000001" },
          invoice:
            stage === "INVOICE_DRAFTED"
              ? {
                  invoiceId: "00000000-0000-4000-8000-000000000197",
                  invoiceNumber: "MOCK-000001",
                  status: "DRAFT",
                }
              : {},
          links: {},
          events: [],
          operations: [],
          allowedActions:
            stage === "TO_SCHEDULE"
              ? ["CREATE_JOB"]
              : stage === "TO_INVOICE"
                ? ["PREPARE_INVOICE"]
                : [],
        },
      });
    });
    await signIn(page, "mywork");
    await expect(page.getByRole("button", { name: "Follow up Q-A" })).toBeVisible();
    await page.getByRole("button", { name: "Refresh sync" }).click();
    await page.getByRole("button", { name: "Create job Q-A" }).click();
    await page.getByRole("button", { name: "Create vWork job" }).click();
    await page.getByLabel("Site address").fill("Example Street");
    await page.getByRole("button", { name: "Create job", exact: true }).click();
    await expect(page.getByText("vWork job linked.")).toBeVisible();
    await page.getByRole("button", { name: "Back to My Work" }).click();
    await page.getByRole("button", { name: "Refresh sync" }).click();
    await page.getByRole("button", { name: "Prepare invoice Q-A" }).click();
    await page.getByRole("button", { name: "Prepare invoice", exact: true }).click();
    await expect(page.getByRole("dialog").getByText("Synthetic Xero organisation")).toBeVisible();
    await page.getByRole("button", { name: "Create test invoice" }).click();
    await expect(page.getByText("Test invoice created.")).toBeVisible();
    await expect(page.getByText("MOCK-000001")).toBeVisible();
    expect(invoiceSubmitted).toEqual({ version: 3, fingerprint: "a".repeat(64) });
  });
}
