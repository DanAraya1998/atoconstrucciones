import { defineConfig } from "@playwright/test";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontend = dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  testDir: resolve(frontend, "tests/e2e"), workers: 1,
  use: { baseURL: "http://127.0.0.1:3345", browserName: "chromium", reducedMotion: "reduce", launchOptions: process.env.ATO_CHROME_PATH ? { executablePath: process.env.ATO_CHROME_PATH } : {} },
  webServer: [
    { command: "node tests/fixtures/public-site-server.mjs", cwd: frontend, url: "http://127.0.0.1:9355/health", reuseExistingServer: false, timeout: 15000 },
    { command: "npm run start", cwd: resolve(frontend, "../target/landing-frontend"), env: { PORT: "3345", ATO_BACKEND_ORIGIN: "http://127.0.0.1:9355" }, url: "http://127.0.0.1:3345", reuseExistingServer: false, timeout: 60000 },
  ],
});
