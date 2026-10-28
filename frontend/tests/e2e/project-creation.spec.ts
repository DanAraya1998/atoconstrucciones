import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";

test.use({ actionTimeout: 10000 });

async function prepare(page: Page, failOnce = false) {
  const clientId = randomUUID(), projectId = randomUUID(), managerId = randomUUID();
  const requests: { code: string; key: string }[] = [];
  let created = false;
  const project = { id: projectId, code: "PRUEBA-001", name: "Obra de prueba", clientId, location: "Ubicación sintética", contactEmail: "project@example.invalid", startsOn: "2026-10-15", estimatedEndsOn: "2027-01-15", state: "BORRADOR", version: 1 };
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path === "/api/v1/session") return route.fulfill({ json: { id: managerId, email: "manager@example.invalid", roles: ["GERENTE"], mfa: false, authenticatedAt: new Date().toISOString() } });
    if (path === "/api/v1/session/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } });
    if (path === "/api/v1/clients") return route.fulfill({ json: { items: [{ id: clientId, name: "Cliente de prueba", active: true }] } });
    if (path === "/api/v1/projects" && request.method() === "POST") {
      expect(request.headers()["x-csrf-token"]).toBe("synthetic");
      const body = request.postDataJSON(); expect(body).toEqual({ code: project.code, name: project.name, clientId, location: project.location, contactEmail: project.contactEmail, startsOn: project.startsOn, estimatedEndsOn: project.estimatedEndsOn, latitude: null, longitude: null });
      requests.push({ code: body.code, key: request.headers()["idempotency-key"] });
      expect(requests.at(-1)!.key).toBeTruthy();
      if (failOnce && requests.length === 1) return route.fulfill({ status: 503, contentType: "application/problem+json", json: { code: "SERVICE_UNAVAILABLE", detail: "Servicio temporalmente no disponible." } });
      created = true; return route.fulfill({ status: 201, json: project });
    }
    if (path === "/api/v1/projects") return route.fulfill({ json: { items: created ? [project] : [], page: 0, size: 20 } });
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/obras");
  const summary = page.locator("summary").filter({ hasText: "Preparar una obra" }); await summary.click();
  const form = summary.locator("..").locator("form");
  await form.getByLabel("Nombre de la obra").fill(project.name);
  await form.getByLabel("Cliente contratante").selectOption(clientId);
  await form.getByLabel("Ubicación descriptiva").fill(project.location);
  await form.getByLabel("Correo de contacto de la obra").fill(project.contactEmail);
  await form.getByLabel("Inicio estimado").fill(project.startsOn);
  await form.getByLabel("Final estimado").fill(project.estimatedEndsOn);
  return { form, requests };
}

test("obra: código legible se normaliza y crea el borrador por teclado en móvil", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const { form, requests } = await prepare(page);
  const code = form.getByLabel("Código de obra", { exact: true });
  await code.fill("Prueba_001"); await expect(form.getByText("Se guardará como PRUEBA-001.")).toBeVisible();
  await code.press("Tab"); await expect(code).toHaveValue("PRUEBA-001");
  await form.getByRole("button", { name: "Crear borrador" }).focus(); await page.keyboard.press("Enter");
  await expect(form.getByRole("status")).toHaveText("Cambio guardado.");
  await expect(page.getByRole("heading", { name: "Obra de prueba", exact: true })).toBeVisible();
  expect(requests).toHaveLength(1); expect(requests[0].code).toBe("PRUEBA-001");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
});

test("obra: código inválido se explica junto al campo y no envía una solicitud", async ({ page }) => {
  const { form, requests } = await prepare(page), code = form.getByLabel("Código de obra", { exact: true });
  for (const value of ["AB", "OB@001", "OB 001", "OBRA/001"]) {
    await code.fill(value); await form.getByRole("button", { name: "Crear borrador" }).click();
    await expect(form.getByRole("alert")).toHaveText("Escribe un código de 3 a 30 caracteres, con letras A-Z, números y guiones (-).");
    await expect(code).toHaveAttribute("aria-invalid", "true"); expect(requests).toHaveLength(0);
  }
  await code.fill("Prueba_001"); await code.press("Tab"); await expect(form.getByRole("alert")).toHaveCount(0);
  await form.getByRole("button", { name: "Crear borrador" }).click(); await expect(form.getByRole("status")).toHaveText("Cambio guardado."); expect(requests).toHaveLength(1);
});

test("obra: Enter sin salir del código y reintento conservan código e idempotencia", async ({ page }) => {
  const { form, requests } = await prepare(page, true), code = form.getByLabel("Código de obra", { exact: true });
  await code.fill("Prueba_001"); await code.press("Enter");
  await expect(form.getByRole("status")).toHaveText("Servicio temporalmente no disponible.");
  await expect(code).toHaveValue("Prueba_001");
  await form.getByRole("button", { name: "Crear borrador" }).click(); await expect(form.getByRole("status")).toHaveText("Cambio guardado.");
  expect(requests).toHaveLength(2); expect(requests[1]).toEqual(requests[0]);
});
