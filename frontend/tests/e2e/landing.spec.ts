import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test.use({ actionTimeout: 15000 });
test.beforeEach(async ({ request }) => { expect((await request.post("http://127.0.0.1:9355/__scenario?value=sample")).ok()).toBe(true); });

test("landing: composición completa con lorem ipsum e imágenes identificadas", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Lorem ipsumdolor sit amet.");
  await expect(page.getByText("Contenido de muestra · Lorem ipsum", { exact: true })).toBeVisible();
  await expect(page.locator("#servicios article")).toHaveCount(3); await expect(page.locator("#galeria article")).toHaveCount(3);
  await expect(page.locator(".landing-hero img")).toBeVisible(); await expect.poll(() => page.locator(".landing-hero img").evaluate(img => (img as HTMLImageElement).naturalWidth)).toBeGreaterThan(100);
  await expect(page.locator(".landing-hero img")).toHaveAttribute("fetchpriority", "high");
  await expect(page.locator(".landing-hero figcaption")).toHaveText("Imagen conceptual · ilustrativa"); await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await page.screenshot({ path: "test-results/landing-desktop-top.png" });
  for (const img of await page.locator(".landing img").all()) { await img.scrollIntoViewIfNeeded(); await expect.poll(() => img.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(100); }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: "test-results/landing-desktop.png", fullPage: true });
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); expect(errors).toEqual([]);
});

test("landing: móvil, teclado, anclas, navegación y accesibilidad", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 }); await page.goto("/");
  await page.keyboard.press("Tab"); await expect(page.getByRole("link", { name: "Saltar al contenido" })).toBeFocused();
  await page.getByRole("link", { name: "Explorar galería", exact: true }).click(); await expect(page).toHaveURL(/#galeria$/); await expect(page.locator("#galeria h2")).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
  await page.goto("/"); await page.locator('summary[aria-label="Menú principal"]').click(); await page.getByRole("navigation", { name: "Navegación principal" }).getByRole("link", { name: "Galería", exact: true }).click(); await expect(page).toHaveURL(/\/proyectos$/); await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.goto("/"); await page.screenshot({ path: "test-results/landing-mobile-top.png" });
  for (const img of await page.locator(".landing img").all()) { await img.scrollIntoViewIfNeeded(); await expect.poll(() => img.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(100); }
  await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: "test-results/landing-mobile.png", fullPage: true });
  for (const path of ["/ato", "/proyectos", "/contacto", "/aviso-privacidad"]) {
    await page.goto(path); await expect(page.getByRole("heading", { level: 1 })).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
  }
});

test("login entra directamente al flujo OIDC", async ({ request }) => {
  const response = await request.get("/login", { maxRedirects: 0 });
  expect(response.status()).toBe(307); expect(response.headers().location).toBe("/auth/login");
});

test("landing: contenido aprobado tiene prioridad y las imágenes públicas siguen sin optimización", async ({ page, request }) => {
  await request.post("http://127.0.0.1:9355/__scenario?value=approved"); await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Titular editorial aprobado"); await expect(page.getByRole("heading", { name: "Servicio aprobado uno" })).toBeVisible(); await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /index, follow/);
  await expect(page.locator(".landing")).not.toContainText("Lorem ipsum"); await expect(page.locator(".landing-hero img")).toHaveAttribute("src", "/api/v1/public/assets/fixture-1");
  await page.getByRole("link", { name: /Publicación autorizada 1/ }).click(); await expect(page.getByRole("heading", { level: 1 })).toHaveText("Publicación autorizada 1");
  expect((await request.get('/_next/image?url=%2Fapi%2Fv1%2Fpublic%2Fassets%2Ffixture-1&w=640&q=75')).status()).toBe(400);
  await request.post("http://127.0.0.1:9355/__scenario?value=withdrawn"); await page.reload(); await expect(page.getByRole("heading", { name: /404/ })).toBeVisible(); expect((await request.get("/api/v1/public/assets/fixture-1")).status()).toBe(404);
});

for (const scenario of ["draft", "unavailable"]) test(`landing: ${scenario} mantiene muestra completa sin revelar borradores`, async ({ page, request }) => {
  await request.post(`http://127.0.0.1:9355/__scenario?value=${scenario}`); await page.goto("/");
  await expect(page.getByText("Contenido de muestra · Lorem ipsum", { exact: true })).toBeVisible(); await expect(page.locator("#servicios article")).toHaveCount(3); await expect(page.locator("#galeria article")).toHaveCount(3);
  expect(await page.locator("body").innerText()).not.toContain("PRIVATE_DRAFT"); await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
});
