import { expect, test } from "@playwright/test";
import { ENTITY_A, JOB_A, greenLane, signIn } from "./support/green-lane";

const summary = {
  id: JOB_A,
  entityId: ENTITY_A,
  entityName: "First entity",
  xeroQuoteId: "00000000-0000-4000-8000-000000000120",
  quoteNumber: "Q-A",
  jobId: "J-A",
  customerName: "Customer A",
  xeroQuoteStatus: "ACCEPTED",
  stage: "INVOICE_CREATED",
  stageEnteredAt: "2026-11-24T01:30:00Z",
  daysInStage: 1,
  late: false,
  nextAction: "REVIEW_AND_SEND",
  flags: [],
  attention: [],
  version: 4,
};
const sam = { id: "00000000-0000-4000-8000-000000000001", displayName: "Sam Example" };
const events = [
  {
    id: 3,
    at: "2026-11-24T01:30:00Z",
    source: "XERO_SYNC",
    eventType: "STAGE_CHANGED",
    fromStage: "JOB_COMPLETED",
    toStage: "INVOICE_CREATED",
    details: { invoiceNumber: "MOCK-000001" },
  },
  {
    id: 2,
    at: "2026-11-23T10:11:00Z",
    source: "VWORK_SYNC",
    eventType: "STAGE_CHANGED",
    fromStage: "WORK_IN_PROGRESS",
    toStage: "JOB_COMPLETED",
    toOwner: sam,
    details: { toJob: { status: "COMPLETED", completedAt: "2026-11-23T09:55:00Z" } },
  },
  {
    id: 1,
    at: "2026-11-23T10:09:00Z",
    actor: sam,
    source: "USER",
    eventType: "OWNER_CHANGED",
    toOwner: { ...sam, displayName: "A-very-long-unbroken-owner-name-that-must-wrap-on-phones" },
  },
];

for (const width of [390, 1440]) {
  test(`GL-07 activity history reads from and to at ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 900 });
    await greenLane(page, "DIRECTOR");
    const headers = { "Access-Control-Allow-Origin": "*" };
    await page.route("**/api/v1/work-items/my-work**", async (route) => {
      if (route.request().method() === "OPTIONS") return route.fallback();
      await route.fulfill({
        headers,
        json: {
          needsAction: [summary],
          waiting: [],
          needsAttention: [],
          generatedAt: "2026-11-24T02:00:00Z",
          truncated: false,
        },
      });
    });
    await page.route(`**/api/v1/entities/${ENTITY_A}/work-items/${JOB_A}`, async (route) => {
      if (route.request().method() === "OPTIONS") return route.fallback();
      await route.fulfill({
        headers,
        json: {
          summary,
          vwork: {},
          invoice: {},
          links: {},
          events,
          operations: [],
          allowedActions: [],
        },
      });
    });
    await signIn(page, "mywork");
    await page.getByRole("button", { name: /Q-A/ }).first().click();
    const activity = page.getByRole("region", { name: "Activity" });
    await expect(activity.getByRole("listitem")).toHaveCount(3);
    await expect(activity.getByText("Job completed", { exact: true }).first()).toBeVisible();
    await expect(activity.getByText("MOCK-000001")).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await activity.screenshot({ path: info.outputPath(`activity-${width}.png`) });
  });
}
