import { defineConfig } from "@playwright/test";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Elegir este config es el opt-in explícito al realm y SMTP sintéticos.
process.env.ATO_E2E_ONBOARDING = "1";
const frontend = dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  testDir: resolve(frontend, "tests/e2e"), workers: 1,
  use: { baseURL: "http://localhost:3302", browserName: "chromium", reducedMotion: "reduce",
    launchOptions: process.env.ATO_CHROME_PATH ? { executablePath: process.env.ATO_CHROME_PATH } : {} },
  webServer: { command: "npm run start", cwd: resolve(frontend, "../target/onboarding-frontend"),
    env: { PORT: "3302", ATO_BACKEND_ORIGIN: "http://127.0.0.1:9194" },
    url: "http://localhost:3302", reuseExistingServer: false, timeout: 60000 },
});
