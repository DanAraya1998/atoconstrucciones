import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";
test.use({ actionTimeout: 10000 });
const account = (role: string, name = "Persona demo") => ({ id: randomUUID(), displayName: name, email: `${randomUUID()}@example.invalid`, roles: [role], enabled: true });
async function open(page: Page, title: string) { const summary = page.locator("summary").filter({ hasText: title }).first(); await summary.click(); return summary.locator(".."); }
for (const kind of ["clients", "suppliers"]) {
  test(`${kind}: buscar, confirmar, conceder y revocar sin introducir identificadores`, async ({ page }) => {
    test.setTimeout(60000); const role = kind === "clients" ? "CLIENTE" : "PROVEEDOR", person = account(role), another = account(role, "Persona alternativa"), partyId = randomUUID(), managerId = randomUUID();
    let version = 1, active = false; const commands: { userId: string; active: boolean }[] = [];
    await page.route("**/api/v1/**", async (route) => {
      const r = route.request(), url = new URL(r.url()), path = url.pathname;
      if (path === "/api/v1/session") return route.fulfill({ json: { id: managerId, email: "manager@example.invalid", roles: ["GERENTE"], mfa: false, authenticatedAt: new Date().toISOString() } });
      if (path === "/api/v1/session/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } });
      if (path === "/api/v1/accounts/search") { expect(r.postDataJSON().role).toBe(role); expect(r.postDataJSON().query).toBe("Persona"); return route.fulfill({ json: { items: [person, another], page: 0, size: 20, hasMore: false } }); }
      const party = { id: partyId, kind: kind === "clients" ? "CLIENT" : "SUPPLIER", name: "Registro demo", email: "contact@example.invalid", phone: "20000000", category: "", active: true, version };
      if (path.endsWith("/member-accounts")) return route.fulfill({ json: { items: commands.length ? [{ account: person, active }] : [], page: 0, size: 20, hasMore: false } });
      if (path === `/api/v1/${kind}/${partyId}/members` && r.method() === "PUT") { expect(r.headers()["x-csrf-token"]).toBe("synthetic"); expect(r.headers()["if-match"]).toBe(`"${version}"`); expect(r.headers()["idempotency-key"]).toBeTruthy(); const body = r.postDataJSON(); commands.push(body); active = body.active; version++; return route.fulfill({ json: { ...party, version } }); }
      if (path === `/api/v1/${kind}`) return route.fulfill({ json: { items: [party], page: 0, size: 20 } });
      return route.fulfill({ json: { items: [], page: 0, size: 20 } });
    });
    await page.setViewportSize({ width: 390, height: 844 }); await page.goto("/cartera"); if (kind === "suppliers") await page.getByRole("button", { name: "Proveedores", exact: true }).click();
    await open(page, "Gestionar acceso de cuentas"); const grant = await open(page, "Vincular una cuenta");
    await grant.getByLabel("Buscar cuenta por nombre o correo").fill("Persona"); await grant.getByLabel("Buscar cuenta por nombre o correo").press("Enter");
    const select = grant.getByRole("combobox", { name: "Cuenta a vincular" }); await select.selectOption({ label: `${person.displayName} · ${person.email}` });
    await grant.getByRole("checkbox").check(); await select.selectOption({ label: `${another.displayName} · ${another.email}` }); await expect(grant.getByRole("checkbox")).not.toBeChecked(); await select.selectOption({ label: `${person.displayName} · ${person.email}` });
    await grant.getByRole("button", { name: "Conceder acceso", exact: true }).click(); expect(commands).toHaveLength(0); await grant.getByRole("checkbox").check(); await grant.getByRole("button", { name: "Conceder acceso", exact: true }).focus(); await page.keyboard.press("Enter");
    await expect(grant.getByRole("status")).toHaveText("Cambio guardado."); expect(commands).toEqual([{ userId: person.id, active: true }]);
    expect(await page.locator("body").innerText()).not.toContain(person.id); await expect(page.getByText("Acceso concedido", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const revoke = await open(page, `Revocar acceso de ${person.email}`);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await revoke.getByRole("button", { name: "Confirmar revocación" }).click(); await expect(page.getByText("Acceso revocado", { exact: true })).toBeVisible(); expect(commands[1]).toEqual({ userId: person.id, active: false });
    await page.screenshot({ path: `../target/accounts-${kind}-mobile.png`, fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
  });
}
test("cuentas: un fallo de búsqueda se puede reintentar", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/v1/**", async route => { const path = new URL(route.request().url()).pathname; if (path === "/api/v1/session") return route.fulfill({ json: { id: randomUUID(), email: "manager@example.invalid", roles: ["GERENTE"], mfa: false, authenticatedAt: new Date().toISOString() } }); if (path === "/api/v1/accounts/search") { attempts++; if (attempts === 1) return route.fulfill({ status: 503, contentType: "application/problem+json", json: { code: "SERVICE_UNAVAILABLE", detail: "Servicio no disponible" } }); return route.fulfill({ json: { items: [account("CLIENTE")], page: 0, size: 20, hasMore: false } }); } if (path === "/api/v1/clients") return route.fulfill({ json: { items: [{ id: randomUUID(), kind: "CLIENT", name: "Registro demo", email: "contact@example.invalid", phone: "20000000", category: "", active: true, version: 1 }] } }); return route.fulfill({ json: { items: [], page: 0, size: 20, hasMore: false } }); });
  await page.goto("/cartera"); await open(page, "Gestionar acceso de cuentas"); const grant = await open(page, "Vincular una cuenta"); await grant.getByLabel("Buscar cuenta por nombre o correo").fill("Persona"); await grant.getByRole("button", { name: "Buscar cuentas" }).click(); await expect(grant.getByRole("alert")).toBeVisible(); await grant.getByRole("button", { name: "Buscar cuentas" }).click(); await expect(grant.getByRole("combobox", { name: "Cuenta a vincular" }).locator("option")).toHaveCount(2); expect(attempts).toBe(2);
});
test("invitación: fragmento privado sobrevive al login y se acepta desde Mi cuenta", async ({ page }) => {
  const id = randomUUID(), token = "synthetic_invitation_token_01234567890123456789", expiresAt = new Date(Date.now() + 86400000).toISOString(); let signedIn = false, accepted = false;
  await page.route("**/api/v1/**", async route => { const path = new URL(route.request().url()).pathname; if (path === "/api/v1/session") return signedIn ? route.fulfill({ json: { id: randomUUID(), email: "invited@example.invalid", roles: accepted ? ["CLIENTE"] : [], mfa: false, authenticatedAt: new Date().toISOString() } }) : route.fulfill({ status: 401, json: { code: "UNAUTHENTICATED" } }); if (path === "/api/v1/session/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } }); if (path === `/api/v1/invitations/${id}/accept`) { expect(route.request().postDataJSON()).toEqual({ token }); expect(route.request().headers()["x-csrf-token"]).toBe("synthetic"); accepted = true; return route.fulfill({ status: 204 }); } return route.fulfill({ json: { items: [] } }); });
  await page.goto(`/invitacion#${new URLSearchParams({ id, token, expiresAt })}`); await expect(page.getByRole("link", { name: "Ingresar para aceptar" })).toBeVisible(); expect(new URL(page.url()).hash).toBe(""); expect(new URL(page.url()).search).toBe(""); expect(await page.evaluate(() => sessionStorage.getItem("ato.pendingInvitation"))).toContain(token);
  signedIn = true; await page.goto("/fundamentos"); await expect(page.getByText("Invitada", { exact: true })).toHaveCount(0); await page.getByRole("button", { name: "Aceptar invitación", exact: true }).click(); await expect(page.getByRole("status").filter({ hasText: "Invitación aceptada." })).toBeVisible(); expect(accepted).toBe(true); expect(await page.evaluate(() => sessionStorage.getItem("ato.pendingInvitation"))).toBeNull(); await expect(page.getByText("Cliente", { exact: true })).toBeVisible();
});
test("invitación: enlace caducado se descarta y un fallo de aceptación permite reintento", async ({ page }) => {
  const id = randomUUID(), token = "synthetic_invitation_token_01234567890123456789"; let attempts = 0;
  await page.route("**/api/v1/**", async route => { const path = new URL(route.request().url()).pathname; if (path === "/api/v1/session") return route.fulfill({ json: { id: randomUUID(), email: "invited@example.invalid", roles: [], mfa: false, authenticatedAt: new Date().toISOString() } }); if (path === "/api/v1/session/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } }); if (path.endsWith("/accept")) { attempts++; return attempts === 1 ? route.fulfill({ status: 404, json: { code: "NOT_FOUND" } }) : route.fulfill({ status: 204 }); } return route.fulfill({ json: { items: [] } }); });
  await page.goto(`/invitacion#${new URLSearchParams({ id, token, expiresAt: new Date(Date.now() - 1000).toISOString() })}`); await expect(page.getByRole("alert").filter({hasText:"ha caducado"})).toContainText("ha caducado"); await page.getByRole("button", { name: "Descartar invitación" }).click(); await expect(page.getByText("Abre el enlace compartido por gerencia para aceptar tu invitación.")).toBeVisible();
  await page.goto(`/invitacion#${new URLSearchParams({ id, token, expiresAt: new Date(Date.now() + 86400000).toISOString() })}`); await page.getByRole("button", { name: "Aceptar invitación", exact: true }).click(); await expect(page.getByRole("alert").filter({hasText:"correo invitado"})).toContainText("correo invitado"); await page.getByRole("button", { name: "Aceptar invitación", exact: true }).click(); await expect(page.getByRole("status")).toContainText("Invitación aceptada."); expect(attempts).toBe(2);
});
