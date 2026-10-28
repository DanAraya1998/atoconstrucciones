import lighthouse from "lighthouse";
import { launch } from "chrome-launcher";
import { mkdirSync, writeFileSync } from "node:fs";
import { URL } from "node:url";
const folder = process.env.ATO_MEASURE_FOLDER ?? "../target/m4-quality";
const target = process.env.ATO_MEASURE_URL ?? "http://localhost:3000";
const url = new URL(target);
if (url.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) throw new Error("La medición local requiere un origen HTTP de loopback.");
const browser = await launch({ chromePath: process.env.ATO_CHROME_PATH, chromeFlags: ["--headless", "--disable-dev-shm-usage", "--no-sandbox"] });
try {
  const result = await lighthouse(target, { port: browser.port, output: "json", onlyCategories: ["performance", "accessibility", "seo"], formFactor: "mobile" });
  mkdirSync(folder, { recursive: true }); writeFileSync(`${folder}/lighthouse.json`, result.report);
  const report = result.lhr;
  const metrics = { performance: report.categories.performance.score, accessibility: report.categories.accessibility.score, seo: report.categories.seo.score, lcpMs: report.audits["largest-contentful-paint"].numericValue, cls: report.audits["cumulative-layout-shift"].numericValue, tbtMs: report.audits["total-blocking-time"].numericValue, scope: "Local mobile lab; TBT is not field INP" };
  writeFileSync(`${folder}/performance.json`, JSON.stringify(metrics, null, 2) + "\n"); console.log(JSON.stringify(metrics));
} finally { await browser.kill(); }
