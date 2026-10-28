import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { createHash } from "node:crypto";

test.skip(!process.env.ATO_E2E_M4_RESTORE, "Requiere el destino restaurado aislado y sus archivos recuperados");
const origin = "http://localhost:3000";
async function login(page: Page, name: string) {
  const password = readFileSync(resolve("../.m0-local/accounts.txt"), "utf8").split("\n").find((line) => line.startsWith(`${name}@example.invalid: `))!.split(": ")[1];
  await page.goto(origin + "/auth/login"); await page.locator("#username").fill(name); await page.locator("#password").fill(password); await page.locator("#kc-login").click(); await expect(page).toHaveURL(/\/fundamentos$/);
}
test("M4 restore: login nuevo, original y variante íntegros, contactos y permisos recuperados", async ({ page, browser }) => {
  test.setTimeout(90000); await login(page, "gerente");
  const publications = await (await page.request.get(origin + "/api/v1/publications")).json();
  const withdrawn = publications.find((p: { state: string; publiclyAvailable: boolean; projectId: string | null }) => p.state === "PUBLISHED" && !p.publiclyAvailable && p.projectId === null); expect(withdrawn).toBeTruthy();
  const photo = withdrawn.photos[0];
  const variant = await page.request.get(origin + `/api/v1/publications/${withdrawn.id}/assets/${photo.id}/preview`); expect(variant.status()).toBe(200); expect(variant.headers()["content-type"]).toContain("image/jpeg"); expect((await variant.body()).length).toBeGreaterThan(0);
  const document = await (await page.request.get(origin + `/api/v1/documents/${photo.documentId}`)).json();
  const original = await page.request.get(origin + `/api/v1/documents/${document.id}/content`); expect(original.status()).toBe(200); expect(createHash("sha256").update(await original.body()).digest("hex")).toBe(document.sha256);
  expect((await (await page.request.get(origin + "/api/v1/contacts")).json()).length).toBeGreaterThan(0);
  await page.goto(origin + "/publicacion"); await expect(page.getByText("Distribución bloqueada por autorización", { exact: false }).first()).toBeVisible();
  const anonymous = await browser.newContext();
  expect((await anonymous.request.get(origin + `/api/v1/public/assets/${photo.id}`)).status()).toBe(404); expect((await anonymous.request.get(origin + `/api/v1/public/projects/${withdrawn.slug}`)).status()).toBe(404); expect((await anonymous.request.get(origin + "/api/v1/contacts")).status()).toBe(401); await anonymous.close();
  const client = await browser.newContext(), cp = await client.newPage(); await login(cp, "cliente-b");
  const own = await (await cp.request.get(origin + "/api/v1/client-projects")).json(); expect(own.items.length).toBeGreaterThan(0);
  const portal = await cp.request.get(origin + `/api/v1/projects/${own.items[0].id}/client-portal`); expect(portal.status()).toBe(200); expect(await portal.text()).not.toContain("PRIVATE-PLAN-SENTINEL");
  expect((await cp.request.get(origin + `/api/v1/projects/${document.projectId}/client-portal`)).status()).toBe(404); expect((await cp.request.get(origin + `/api/v1/documents/${document.id}/content`)).status()).toBe(404); expect((await cp.request.get(origin + "/api/v1/contacts")).status()).toBe(403); await client.close();
  const supplier = await browser.newContext(), sp = await supplier.newPage(); await login(sp, "proveedor");
  const assigned = await (await sp.request.get(origin + "/api/v1/projects?size=100")).json(); expect(assigned.items.length).toBeGreaterThan(0);
  let tasks = 0; for (const project of assigned.items) { const response = await sp.request.get(origin + `/api/v1/projects/${project.id}/schedule/tasks`); if (response.ok()) tasks += (await response.json()).items.length; } expect(tasks).toBeGreaterThan(0);
  expect((await sp.request.get(origin + "/api/v1/contacts")).status()).toBe(403); await supplier.close();
  const admin = await browser.newContext(), ap = await admin.newPage(); await login(ap, "admin");
  const account = await (await ap.request.get(origin + "/api/v1/session")).json(); expect(account.roles).toEqual(["ADMIN"]); expect((await ap.request.get(origin + "/api/v1/operations/status")).status()).toBe(200); expect((await ap.request.get(origin + "/api/v1/contacts")).status()).toBe(403); expect((await ap.request.get(origin + `/api/v1/documents/${document.id}/content`)).status()).toBe(404); await admin.close();
});
