import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test.skip(process.env.ATO_E2E_ONBOARDING !== "1", "Requiere el Keycloak aislado con tema ATO instalado");
test.use({ baseURL: "http://localhost:3302", actionTimeout: 15000 });
async function accessible(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
}

test("tema ATO: acceso directo, recursos, teclado, errores, idioma y móvil", async ({ page }) => {
  test.setTimeout(90000);
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto("/");
  await page.getByRole("navigation", { name: "Navegación principal" }).getByRole("link", { name: "Login", exact: true }).click();
  await expect(page).toHaveURL(/^http:\/\/localhost:8182\/realms\/ato-onboarding\//);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Bienvenido a tu espacio");
  await expect(page.locator("body")).toHaveClass(/ato-auth/);
  const css = await page.locator('link[href$="/css/ato.css"]').getAttribute("href"); expect(css).toBeTruthy();
  expect((await page.request.get(new URL(css!, page.url()).href)).status()).toBe(200);
  for (const asset of ["ato-logo.jpg", "ato-concept-exterior.webp"]) {
    expect((await page.request.get(new URL(css!.replace("css/ato.css", `img/${asset}`), page.url()).href)).status()).toBe(200);
  }
  await expect(page.getByRole("link", { name: "Volver al sitio" })).toHaveAttribute("href", "http://localhost:3302/");
  await accessible(page); await page.screenshot({ path: "../target/theme-preview/login-desktop.png", fullPage: true });
  await page.locator("#username").fill("no-existe@example.invalid"); await page.locator("#password").fill("Synthetic-invalid-password!");
  await page.locator("#kc-login").focus(); await page.keyboard.press("Enter");
  await expect(page.locator('[aria-invalid="true"]').first()).toBeVisible(); await accessible(page);
  await page.setViewportSize({ width: 360, height: 800 }); await page.goto("/");
  await page.locator('summary[aria-label="Menú principal"]').click();
  await page.getByRole("navigation", { name: "Navegación principal" }).getByRole("link", { name: "Login", exact: true }).click();
  await accessible(page); await page.screenshot({ path: "../target/theme-preview/login-mobile.png", fullPage: true });
  await page.locator("#username").focus(); await page.keyboard.press("Tab"); await expect(page.locator("#password")).toBeFocused();
  await page.locator("#login-select-toggle").selectOption((await page.locator('#login-select-toggle option[value*="kc_locale=en"]').getAttribute("value"))!);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Welcome to your workspace"); await accessible(page);
  await page.locator("#login-select-toggle").selectOption((await page.locator('#login-select-toggle option[value*="kc_locale=es"]').getAttribute("value"))!);
  const accounts = await readFile(resolve("../.m0-local/accounts.txt"), "utf8");
  const password = accounts.split("\n").find(line => line.startsWith("gerente@example.invalid: "))!.split(": ")[1];
  await page.locator("#username").fill("gerente"); await page.locator("#password").fill(password);
  await page.locator("#kc-login").focus(); await page.keyboard.press("Enter"); await expect(page).toHaveURL(/\/fundamentos$/);
  const session = await (await page.request.get("/api/v1/session")).json(); expect(session.roles).toContain("GERENTE"); expect(session.mfa).toBe(false);
});

test("tema ATO: registro y recuperación accesibles sin exponer cuentas inexistentes", async ({ page }) => {
  test.setTimeout(90000); await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/login"); await page.getByRole("link", { name: "Crear cuenta", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Crear una cuenta"); await accessible(page);
  await page.screenshot({ path: "../target/theme-preview/register-mobile.png", fullPage: true });
  await page.goto("/login"); await page.getByRole("link", { name: "¿Olvidaste tu contraseña?", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Recuperar tu acceso"); await accessible(page);
  await page.screenshot({ path: "../target/theme-preview/recovery-mobile.png", fullPage: true });
  await page.locator("#username").fill(`ausente-${Date.now()}@example.invalid`);
  await page.locator('button[type="submit"],input[type="submit"]').first().click();
  await expect(page.locator(".kc-feedback-text")).toHaveText("En breve deberías recibir un mensaje con más instrucciones.");
  await accessible(page); await page.getByRole("link", { name: "Volver al sitio" }).click(); await expect(page).toHaveURL("http://localhost:3302/");
  await page.getByRole("link", { name: "Acceso a mi cuenta", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Bienvenido a tu espacio");
});
