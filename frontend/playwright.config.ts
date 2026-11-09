import { defineConfig, devices } from "@playwright/test";

const port = 4173;

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  outputDir: "../output/playwright/test-results",
  globalSetup: "./e2e/setup.ts",
  reporter: [["line"], ["json", { outputFile: "../output/playwright/report.json" }]],
  use: {
    baseURL: `http://localhost:${port}`,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
