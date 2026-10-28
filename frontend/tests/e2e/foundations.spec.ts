import { test, expect } from "@playwright/test";

test("inicio accesible en móvil y navegación por teclado", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.keyboard.press("Tab"); await expect(page.getByRole("link", { name: "Saltar al contenido" })).toBeFocused();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
test("SVAR y Next se hidratan sin errores con reduced motion", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  await page.goto("/fundamentos/compatibilidad");
  await expect(page.getByTestId("gantt-spike")).toContainText("Preparación");
  await expect(page.getByTestId("gantt-spike")).toContainText("Revisión");
  expect(await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches)).toBeTruthy();
  expect(errors).toEqual([]);
});
test("portal maneja sesión ausente y una mutación protegida", async ({ page }) => {
  await page.route("**/api/v1/session", (route) => route.fulfill({ status: 401, contentType: "application/problem+json", body: JSON.stringify({ status: 401, code: "UNAUTHENTICATED", detail: "Inicie sesión" }) }));
  await page.goto("/fundamentos"); await expect(page.getByRole("link", { name: "Ingresar", exact: true })).toBeVisible();
  await page.unroute("**/api/v1/session");
  await page.route("**/api/v1/session", (route) => route.fulfill({ json: { id: "synthetic", email: "gerente@example.invalid", roles: ["GERENTE"], mfa: true, authenticatedAt: "2026-10-07T00:00:00Z" } }));
  await page.route("**/api/v1/session/csrf", (route) => route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } }));
  let created = false;
  await page.route("**/api/v1/foundation/resources", (route) => {
    if (route.request().method() === "POST") {
      expect(route.request().headers()["x-csrf-token"]).toBe("synthetic"); expect(route.request().headers()["idempotency-key"]).toBeTruthy(); created = true;
      return route.fulfill({ status: 201, json: { id: "resource", name: "Ensayo", version: 1 } });
    }
    return route.fulfill({ json: { items: created ? [{ id: "resource", name: "Ensayo", version: 1 }] : [], page: 0, size: 20 } });
  });
  await page.reload(); await page.getByLabel("Nombre del recurso").fill("Ensayo"); await page.getByRole("button", { name: "Crear recurso de prueba" }).click();
  await expect(page.getByText("Recurso creado.", { exact: true })).toBeVisible(); await expect(page.getByRole("listitem").getByText("Ensayo", { exact: true })).toBeVisible();
});
