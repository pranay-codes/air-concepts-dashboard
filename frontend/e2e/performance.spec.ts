import { expect, test } from "@playwright/test";
import { greenLane, signIn } from "./support/green-lane";

declare global {
  interface Window {
    uc5Lab: { lcpMs: number; cls: number; maxInteractionMs: number };
  }
}

for (const width of [390, 1440]) {
  test(`FR-UC15-31 production lab measurements at ${width}px`, async ({
    page,
    browserName,
  }, testInfo) => {
    test.skip(
      browserName !== "chromium",
      "PerformanceObserver lab sample uses Chromium event timing.",
    );
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() => {
      window.uc5Lab = { lcpMs: 0, cls: 0, maxInteractionMs: 0 };
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries())
          window.uc5Lab.lcpMs = Math.max(window.uc5Lab.lcpMs, entry.startTime);
      }).observe({ type: "largest-contentful-paint", buffered: true });
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries())
          if (
            "hadRecentInput" in entry &&
            !entry.hadRecentInput &&
            "value" in entry &&
            typeof entry.value === "number"
          )
            window.uc5Lab.cls += entry.value;
      }).observe({ type: "layout-shift", buffered: true });
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries())
          if ("interactionId" in entry && entry.interactionId)
            window.uc5Lab.maxInteractionMs = Math.max(
              window.uc5Lab.maxInteractionMs,
              entry.duration,
            );
      }).observe({
        type: "event",
        buffered: true,
        durationThreshold: 16,
      } as PerformanceObserverInit);
    });
    const state = await greenLane(page);
    state.invoiceCount = 26;
    const started = Date.now();
    await signIn(page);
    await expect(
      page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
    ).toBeVisible();
    const workspaceJourneyMs = Date.now() - started;
    await page.getByLabel("Search invoices").pressSequentially("Acme");
    await expect
      .poll(() =>
        state.requests.some(
          (request) => new URLSearchParams(request.query).get("search") === "Acme",
        ),
      )
      .toBe(true);
    const measurements = await page.evaluate(() => ({
      ...window.uc5Lab,
      userAgent: navigator.userAgent,
    }));
    expect(measurements.lcpMs).toBeGreaterThan(0);
    await testInfo.attach(`production-lab-${width}`, {
      contentType: "application/json",
      body: JSON.stringify(
        {
          ...measurements,
          workspaceJourneyMs,
          width,
          height: 900,
          rows: 26,
          pageSize: 25,
          conditions:
            "Headless Chromium; production build; local synthetic APIs; no CPU/network throttling. Single lab sample, not field p75 or a certified INP measurement.",
        },
        null,
        2,
      ),
    });
  });
}
