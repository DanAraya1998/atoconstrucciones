import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e",
  workers: process.env.ATO_E2E_M4 ? 1 : undefined,
  use: { baseURL: "http://127.0.0.1:3000", browserName: "chromium", reducedMotion: "reduce",
    launchOptions: process.env.ATO_CHROME_PATH ? { executablePath: process.env.ATO_CHROME_PATH } : {} },
  webServer: { command: "npm run start", env: { ATO_BACKEND_ORIGIN: process.env.ATO_BACKEND_ORIGIN ?? (process.env.ATO_E2E_M2 || process.env.ATO_E2E_M3 || process.env.ATO_E2E_M4 ? "http://127.0.0.1:9192" : "http://127.0.0.1:8080") }, url: "http://127.0.0.1:3000", reuseExistingServer: !process.env.CI, timeout: 60000 },
});
