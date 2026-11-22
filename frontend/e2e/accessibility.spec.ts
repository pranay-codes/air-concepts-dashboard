import { expect, test } from "@playwright/test";
import { greenLane, signIn } from "./support/green-lane";

test("FR-UC15-31 mobile workspace reflows with doubled root text size", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await greenLane(page);
  await signIn(page);
  await page.addStyleTag({ content: "html { font-size: 200%; }" });
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("FR-UC15-31 workspace landmarks, labelled controls, targets, skip link and semantic contrast", async ({
  page,
}) => {
  await greenLane(page);
  await signIn(page);
  await expect(
    page.getByRole("button", { name: "INV-001", exact: true }).filter({ visible: true }),
  ).toBeVisible();
  await expect(page.getByRole("main")).toHaveCount(1);
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  const controls = page
    .locator("input:not([type=checkbox]), select, button")
    .filter({ visible: true });
  for (const control of await controls.all()) {
    const result = await control.evaluate((element) => {
      const box = element.getBoundingClientRect();
      const labels =
        element instanceof HTMLInputElement || element instanceof HTMLSelectElement
          ? [...(element.labels ?? [])].map((label) => label.textContent).join(" ")
          : element.textContent;
      return {
        name: element.getAttribute("aria-label") || labels?.trim(),
        width: box.width,
        height: box.height,
      };
    });
    expect(result.name).toBeTruthy();
    expect(result.width).toBeGreaterThanOrEqual(24);
    expect(result.height).toBeGreaterThanOrEqual(24);
  }
  const contrasts = await page.evaluate(() => {
    const style = getComputedStyle(document.documentElement);
    const luminance = (token: string) => {
      let hex = style.getPropertyValue(token).trim().replace("#", "");
      if (hex.length === 3) hex = [...hex].map((character) => character + character).join("");
      const channels = [0, 2, 4]
        .map((offset) => parseInt(hex.slice(offset, offset + 2), 16) / 255)
        .map((channel) =>
          channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4,
        );
      return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722;
    };
    return [
      ["--text", "--surface"],
      ["--secondary", "--workspace"],
      ["--accent", "--selected"],
      ["--success", "--success-surface"],
      ["--warning", "--warning-surface"],
      ["--error", "--error-surface"],
      ["--information", "--information-surface"],
    ].map(([text, background]) => {
      const first = luminance(text!),
        second = luminance(background!);
      return {
        text,
        background,
        ratio: (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05),
      };
    });
  });
  for (const contrast of contrasts)
    expect(contrast.ratio, JSON.stringify(contrast)).toBeGreaterThanOrEqual(4.5);
  await page.reload();
  await expect(page.getByRole("heading", { name: "My Work", exact: true })).toBeVisible();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to content", exact: true })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
});
