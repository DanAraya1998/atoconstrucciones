import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test.skip(process.env.ATO_E2E_ONBOARDING !== "1", "Requiere Keycloak/SMTP/PostgreSQL aislados y opt-in explícito");
test.use({ baseURL: "http://localhost:3302", actionTimeout: 15000 });
test.setTimeout(180000);
const local = resolve(process.cwd(), "../.m0-local");
async function managerLogin(page: Page) {
  const accounts = await readFile(resolve(local, "accounts.txt"), "utf8");
  const password = accounts.split("\n").find(line => line.startsWith("gerente@example.invalid: "))!.split(": ")[1];
  await page.goto("/auth/login"); await page.locator("#username").fill("gerente"); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
  await expect(page.getByRole("link", { name: "Usuarios y accesos", exact: true })).toBeVisible();
}
async function command(page: Page, path: string, input: unknown) {
  const csrf = await (await page.request.get("/api/v1/session/csrf")).json() as { headerName: string; token: string };
  const response = await page.request.post(path, { data: input, headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() } });
  expect(response.ok()).toBe(true); return response.json() as Promise<{ id: string; version: number }>;
}
async function emailLink(address: string) {
  let link = "";
  await expect.poll(async () => {
    const messages = JSON.parse(await readFile(resolve(local, "onboarding-isolated/mail.json"), "utf8")) as { recipients: string[]; links: string[] }[];
    link = messages.filter(m => m.recipients.includes(address)).reverse().flatMap(m => m.links).find(url => url.includes("/login-actions/action-token?")) ?? "";
    return Boolean(link);
  }, { timeout: 20000, message: "Se recibe correo sintético de verificación/activación" }).toBe(true);
  return link;
}

test("alta directa y registro propio convergen en cuentas verificadas con aprobación, obras y suspensión", async ({ browser }) => {
  const manager = await browser.newContext({ baseURL: "http://localhost:3302" }), applicant = await browser.newContext({ baseURL: "http://localhost:3302" }), direct = await browser.newContext({ baseURL: "http://localhost:3302" }), recovery = await browser.newContext({ baseURL: "http://localhost:3302" });
  const admin = await manager.newPage(), user = await applicant.newPage(), createdUser = await direct.newPage(), recoveredUser = await recovery.newPage();
  try {
    await managerLogin(admin);
    const suffix = randomUUID().slice(0, 8), email = `registro-${suffix}@example.invalid`, directEmail = `alta-${suffix}@example.invalid`, password = `Synthetic-${randomUUID()}!`;
    const client = await command(admin, "/api/v1/clients", { name: `Cliente ${suffix}`, email: `contact-${suffix}@example.invalid`, phone: "20000000", category: "", active: true });
    const project = await command(admin, "/api/v1/projects", { code: `ALTA-${suffix.toUpperCase()}`, name: `Obra ${suffix}`, clientId: client.id, location: "Ubicación sintética", contactEmail: `project-${suffix}@example.invalid`, startsOn: "2026-10-01", estimatedEndsOn: "2026-12-01" });
    await user.goto("/login"); await user.getByRole("link", { name: "Crear cuenta", exact: true }).click();
    await user.locator("#firstName").fill("Solicitud"); await user.locator("#lastName").fill(suffix); await user.locator("#email").fill(email);
    if(await user.locator("#password").isVisible()){await user.locator("#password").fill(password);await user.locator("#password-confirm").fill(password);}
    await user.locator('input[type="submit"],button[type="submit"]').first().click();
    await user.goto(await emailLink(email));
    await user.locator("#password-new").or(user.getByRole("heading",{name:"Tu acceso está pendiente de aprobación"})).first().waitFor({timeout:20000});
    if(await user.locator("#password-new").isVisible()){await user.locator("#password-new").fill(password);await user.locator("#password-confirm").fill(password);await user.locator('input[type="submit"],button[type="submit"]').first().click();}
    await expect(user.getByRole("heading", { name: "Tu acceso está pendiente de aprobación" })).toBeVisible({ timeout: 20000 });
    await expect(user.getByRole("link", { name: "Mis obras", exact: true })).toHaveCount(0);
    expect((await user.request.get("/api/v1/accounts/capabilities")).status()).toBe(403);
    await admin.setViewportSize({width:390,height:844});await admin.goto("/usuarios");
    const request = admin.getByRole("article").filter({ hasText: email }); await expect(request).toHaveCount(1);
    await request.locator("summary").filter({ hasText: "Revisar y aprobar" }).click();
    await request.getByLabel("Selecciona el cliente").selectOption(client.id);
    await request.getByRole("checkbox", { name: `Obra ${suffix} · ALTA-${suffix.toUpperCase()}`, exact: true }).check();
    await request.getByRole("checkbox", { name: /Confirmo la identidad/ }).check();
    expect(await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await request.getByRole("button", { name: "Aprobar y conceder acceso", exact: true }).focus();await admin.keyboard.press("Enter");
    await expect(request).toHaveCount(0);
    await user.getByRole("button", { name: "Consultar estado", exact: true }).click(); await expect(user.getByRole("link", { name: "Mis obras", exact: true })).toBeVisible();
    await user.goto("/cliente"); await expect(user.getByText(`Obra ${suffix}`, { exact: true }).first()).toBeVisible();
    const detail = await user.request.get(`/api/v1/projects/${project.id}`); expect(detail.status()).toBe(200);
    await admin.getByLabel("Estado de las cuentas").selectOption("ALL");
    await admin.locator("summary").filter({ hasText: "Crear una cuenta" }).click();
    const form = admin.locator("details").filter({ has: admin.locator("summary").filter({ hasText: "Crear una cuenta" }) }).first();
    await form.getByLabel("Nombre de la persona", { exact: true }).fill(`Alta ${suffix}`); await form.getByLabel("Correo de la persona", { exact: true }).fill(directEmail); await form.getByLabel("Perfil de acceso").selectOption("ASISTENTE_ADMINISTRATIVA");
    await form.getByRole("checkbox", { name: `Obra ${suffix} · ALTA-${suffix.toUpperCase()}`, exact: true }).check(); await form.getByRole("checkbox", { name: /Confirmo la identidad/ }).check();
    await form.getByRole("button", { name: "Crear y habilitar", exact: true }).click(); await expect(form.getByRole("status")).toContainText("Cambio guardado.");
    const account = admin.getByRole("article").filter({ hasText: directEmail }); await expect(account).toContainText("Aprobada");
    await createdUser.goto(await emailLink(directEmail));
    if(await createdUser.locator("#kc-info-message a").isVisible())await createdUser.locator("#kc-info-message a").first().click();
    if (await createdUser.locator("#lastName").isVisible()) { await createdUser.locator("#lastName").fill(suffix); await createdUser.locator('input[type="submit"],button[type="submit"]').first().click(); }
    await createdUser.locator("#password-new").fill(password); await createdUser.locator("#password-confirm").fill(password); await createdUser.locator('input[type="submit"],button[type="submit"]').first().click();
    await createdUser.goto("/auth/login"); await createdUser.locator("#username").fill(directEmail); await createdUser.locator("#password").fill(password); await createdUser.locator("#kc-login").click();
    if (await createdUser.locator("#lastName").isVisible()) { await createdUser.locator("#lastName").fill(suffix); await createdUser.locator('input[type="submit"],button[type="submit"]').first().click(); }
    await expect(createdUser.getByRole("link", { name: "Obras", exact: true })).toBeVisible();
    await createdUser.goto("/obras"); await expect(createdUser.getByText(`Obra ${suffix}`, { exact: true }).first()).toBeVisible();
    await admin.reload(); await admin.getByLabel("Estado de las cuentas").selectOption("ALL");
    const approved = admin.getByRole("article").filter({ hasText: directEmail }); await approved.locator("summary").filter({ hasText: "Suspender cuenta" }).click(); await approved.getByLabel("Motivo").fill("Fin de prueba sintética"); await approved.getByRole("button", { name: "Confirmar suspensión" }).click(); await expect(approved).toContainText("Suspendida");
    expect((await createdUser.request.get("/api/v1/session")).status()).toBe(401);
    await admin.setViewportSize({ width: 390, height: 844 }); expect(await admin.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page: admin }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
    await admin.screenshot({ path: "../target/onboarding-users-mobile.png", fullPage: true });
    // Recovery remains native to Keycloak; a new password preserves the approved identity and project access.
    const newPassword = `Recovered-${randomUUID()}!`;
    await recoveredUser.goto("/login"); await recoveredUser.getByRole("link", { name: "¿Olvidaste tu contraseña?", exact: true }).click();
    await expect(recoveredUser.getByRole("heading", { level: 1 })).toHaveText("Recuperar tu acceso");
    await recoveredUser.locator("#username").fill(email); await recoveredUser.locator('button[type="submit"],input[type="submit"]').first().click();
    await expect(recoveredUser.locator(".kc-feedback-text")).toHaveText("En breve deberías recibir un mensaje con más instrucciones.");
    await expect.poll(async () => {
      const messages = JSON.parse(await readFile(resolve(local, "onboarding-isolated/mail.json"), "utf8")) as { recipients: string[] }[];
      return messages.filter(message => message.recipients.includes(email)).length;
    }).toBeGreaterThan(1);
    await recoveredUser.goto(await emailLink(email));
    await recoveredUser.locator("#password-new").fill(newPassword); await recoveredUser.locator("#password-confirm").fill(newPassword);
    await recoveredUser.locator('button[type="submit"],input[type="submit"]').first().click();
    await recoveredUser.goto("/login"); await recoveredUser.locator("#username").fill(email); await recoveredUser.locator("#password").fill(newPassword); await recoveredUser.locator("#kc-login").click();
    await recoveredUser.goto("/cliente"); await expect(recoveredUser.getByText(`Obra ${suffix}`, { exact: true }).first()).toBeVisible();
    expect((await recoveredUser.request.get(`/api/v1/projects/${project.id}`)).status()).toBe(200);
  } finally { await manager.close(); await applicant.close(); await direct.close(); await recovery.close(); }
});
