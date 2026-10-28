import { test, expect, type Page, type APIRequestContext } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID, createHash } from "node:crypto";
test.use({ actionTimeout: 15000 });
const origin = "http://localhost:3000";
async function login(page: Page, name: string) {
  const password = readFileSync(resolve("../.m0-local/accounts.txt"), "utf8").split("\n").find((l) => l.startsWith(`${name}@example.invalid: `))!.split(": ")[1];
  await page.goto(origin + "/auth/login"); await page.locator("#username").fill(name); await page.locator("#password").fill(password); await page.locator("#kc-login").click(); await expect(page).toHaveURL(/\/fundamentos$/);
  return (await page.request.get(origin + "/api/v1/session")).json();
}
async function command(request: APIRequestContext, path: string, data: unknown, version?: number, method = "POST") {
  const csrf = await (await request.get(origin + "/api/v1/session/csrf")).json(); const result = await request.fetch(origin + path, { method, data, headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID(), ...(version ? { "If-Match": `"${version}"` } : {}) } });
  expect(result.status(), `${path}: ${await result.text()}`).toBeLessThan(300); return result.status() === 204 ? undefined : result.json();
}
async function notice(request: APIRequestContext) {
  const draft = await command(request, "/api/v1/privacy/notices", { controllerName: "Responsable sintético", contactEmail: "privacy@example.invalid", content: "Aviso sintético para consulta y portafolio web." });
  return command(request, `/api/v1/privacy/notices/${draft.id}/approve`, undefined);
}
async function photo(request: APIRequestContext, projectId: string) {
  const bytes = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4nGNgQAYAAA4AAamRc7EAAAAASUVORK5CYII=", "base64");
  const session = await command(request, "/api/v1/documents/upload-sessions", { projectId, filename: "foto-sintetica.png", mediaType: "image/png", size: bytes.length, sha256: createHash("sha256").update(bytes).digest("hex"), documentClass: "PHOTO" });
  const csrf = await (await request.get(origin + "/api/v1/session/csrf")).json(); expect((await request.put(origin + session.uploadPath, { data: bytes, headers: { [csrf.headerName]: csrf.token, "Content-Type": "application/octet-stream" } })).status()).toBe(202);
  await command(request, `/api/v1/documents/${session.document.id}/complete-upload`, undefined); await expect.poll(async () => (await (await request.get(origin + `/api/v1/documents/${session.document.id}`)).json()).state).toBe("CLEAN"); return session.document.id;
}
async function openForm(page: Page, title: string) { const details = page.locator("details").filter({ has: page.locator("summary").filter({ hasText: title }) }).first(); await details.locator("summary").first().click(); return details.locator("form").first(); }
test("M4: navegación pública móvil, teclado, metadata y accesibilidad automatizada", async ({ page }) => {
  test.setTimeout(120000); await page.setViewportSize({ width: 360, height: 800 });
  for (const path of ["/", "/ato", "/proyectos", "/contacto", "/login", "/aviso-privacidad"]) {
    await page.goto(path); await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze(); expect(axe.violations).toEqual([]);
  }
  await page.goto("/"); await page.keyboard.press("Tab"); await expect(page.getByRole("link", { name: "Saltar al contenido" })).toBeFocused();
  await page.locator('summary[aria-label="Menú principal"]').click(); await expect(page.getByRole("navigation", { name: "Navegación principal" })).toBeVisible(); await page.getByRole("navigation", { name: "Navegación principal" }).getByRole("link", { name: "Contacto" }).click(); await expect(page).toHaveURL(/\/contacto$/);
  const sitemap = await (await page.request.get("/sitemap.xml")).text(); for (const secret of ["/obras", "/cliente", "/finanzas", "/cartera", "/publicacion", "/fundamentos", "/login"]) expect(sitemap).not.toContain(`<loc>https://atoconstrucciones.com${secret}`);
  await page.goto("/publicacion"); await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/); await page.screenshot({ path: "test-results/m4-public-mobile.png", fullPage: true });
});
test("M4: editor, publicación, consentimiento retirado y proyección pública segura", async ({ page, browser }) => {
  test.skip(!process.env.ATO_E2E_M4, "Requiere el harness M4 con OIDC real"); test.setTimeout(120000); await login(page, "gerente");
  await page.goto(origin + "/publicacion"); const site = await openForm(page, "Editar textos públicos"); await site.getByLabel("Titular de inicio").fill("Obras con información revisada."); await site.getByLabel("Presentación").fill("Texto sintético aprobado para el ensayo M4."); await site.getByLabel("Texto de ATO").fill("Descripción sintética de la empresa."); await site.getByLabel("Servicios").fill("Servicio sintético"); await site.getByRole("button", { name: "Guardar borrador de textos" }).click(); await expect(site.getByRole("status")).toHaveText("Cambio guardado.");
  const approval = await openForm(page, "Aprobar textos públicos"); await approval.getByRole("button", { name: "Aprobar textos", exact: true }).click(); await expect(page.getByRole("heading", { name: "Textos del sitio · Aprobados" })).toBeVisible();
  const suffix = randomUUID().slice(0, 8); const party = await command(page.request, "/api/v1/clients", { name: `PRIVATE-${suffix}`, email: "client@example.invalid", phone: "20000000", category: "", active: true });
  const project = await command(page.request, "/api/v1/projects", { code: `M4-${suffix.toUpperCase()}`, name: `Custodia M4 ${suffix}`, clientId: party.id, location: "PRIVATE-EXACT-ADDRESS", contactEmail: "private@example.invalid", startsOn: "2026-10-01", estimatedEndsOn: "2026-12-01" });
  const doc = await photo(page.request, project.id), n = await notice(page.request);
  await command(page.request, "/api/v1/privacy/consents", { projectId: project.id, documentId: doc, noticeId: n.id, evidenceId: doc, purpose: "PORTFOLIO", channels: "WEBSITE", expiresOn: "2027-12-31" });
  await page.goto(origin + "/publicacion"); await page.getByLabel("Obra que conserva las fotografías").selectOption(project.id);
  const form = await openForm(page, "Crear ficha pública en borrador"); await form.getByLabel("Título público", { exact: true }).fill(`Portafolio M4 ${suffix}`); await form.getByLabel("URL de la ficha").fill(`portafolio-m4-${suffix}`); await form.getByLabel("Descripción pública").fill("Alcance público revisado y sintético."); await form.getByLabel("Categoría", { exact: true }).fill("Residencial"); await form.getByLabel("Ficha de portafolio independiente").check(); await form.getByLabel("Fotografía de portada").selectOption(doc);
  await expect(form.getByLabel("Consentimiento vigente").locator("option")).toHaveCount(1); await form.getByLabel("Descripción accesible").fill("Vista sintética del portafolio"); await form.getByRole("button", { name: "Preparar ficha" }).click(); await expect(form.getByRole("status")).toHaveText("Cambio guardado.");
  let publication = (await (await page.request.get(origin + "/api/v1/publications")).json()).find((p: { slug: string }) => p.slug === `portafolio-m4-${suffix}`);
  const preview = page.locator("article").filter({ has: page.getByRole("heading", { name: `Portafolio M4 ${suffix}`, exact: true }) }).getByAltText("Vista sintética del portafolio"); await expect(preview).toBeVisible(); await expect.poll(() => preview.evaluate((img) => (img as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  const adminContext = await browser.newContext(), ap = await adminContext.newPage(), admin = await login(ap, "admin"); await command(page.request, `/api/v1/users/${admin.id}/roles`, { roles: ["ADMIN"] }, undefined, "PUT");
  expect((await ap.request.get(origin + "/api/v1/contacts")).status()).toBe(403); expect((await ap.request.get(origin + `/api/v1/publications/${publication.id}/assets/${publication.photos[0].id}/preview`)).status()).toBe(403); expect((await ap.request.get(origin + "/api/v1/operations/status")).status()).toBe(200); await ap.goto(origin + "/publicacion"); await expect(ap.getByText("No tienes permiso para preparar publicaciones o revisar contactos.")).toBeVisible(); await adminContext.close();
  const publish = await openForm(page, `Publicar Portafolio M4 ${suffix}`); await publish.getByLabel("Motivo de aprobación").fill("Revisión editorial y visual sintética"); await publish.getByLabel("Revisé las fotos").check(); await publish.getByRole("button", { name: "Publicar ficha" }).click(); await expect(page.getByRole("link", { name: "Ver ficha pública" }).first()).toBeVisible();
  const visitor = await browser.newContext(), vp = await visitor.newPage(); await vp.goto(origin + `/proyectos/${publication.slug}`); await expect(vp.getByRole("heading", { level: 1 })).toHaveText(`Portafolio M4 ${suffix}`); await expect(vp.getByAltText("Vista sintética del portafolio")).toBeVisible();
  const dto = await (await vp.request.get(origin + `/api/v1/public/projects/${publication.slug}`)).json(); expect(JSON.stringify(dto)).not.toContain(project.id); expect(JSON.stringify(dto)).not.toContain(doc); expect(JSON.stringify(dto)).not.toContain("PRIVATE-"); expect((await vp.request.get(origin + dto.photos[0].url)).headers()["cache-control"]).toBe("no-store"); expect((await vp.request.get(origin + `/_next/image?url=${encodeURIComponent(dto.photos[0].url)}&w=640&q=75`)).status()).toBe(400);
  expect((await new AxeBuilder({ page: vp }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze()).violations).toEqual([]);
  publication = await (await page.request.get(origin + `/api/v1/publications/${publication.id}`)).json(); const consent = (await (await page.request.get(origin + `/api/v1/privacy/consents?projectId=${project.id}`)).json())[0]; await command(page.request, `/api/v1/privacy/consents/${consent.id}/withdraw`, { reason: "Titular retira autorización" }, consent.version);
  expect((await vp.request.get(origin + dto.photos[0].url)).status()).toBe(404); expect((await vp.request.get(origin + `/api/v1/public/projects/${publication.slug}`)).status()).toBe(404); await vp.reload(); await expect(vp.getByRole("heading", { name: /404/ })).toBeVisible();
  await visitor.close();
});
test("M4: contacto conserva reintento, no refleja HTML y se revisa en privado", async ({ page, browser }) => {
  test.skip(!process.env.ATO_E2E_M4, "Requiere harness M4"); test.setTimeout(90000); await login(page, "gerente"); await notice(page.request);
  const visitor = await browser.newContext(), vp = await visitor.newPage(); await vp.goto(origin + "/contacto"); const name = `Contacto M4 ${randomUUID().slice(0, 8)}`;
  await vp.getByLabel("Nombre de contacto").fill(name); await vp.getByLabel("Correo para responder").fill("visitor@example.invalid"); await vp.getByLabel("Cuéntanos sobre tu proyecto").fill("<script>Texto de una consulta sintética</script>"); await vp.getByRole("checkbox").check();
  let lost = false; await vp.route("**/api/v1/public/contact", async (route) => { if (!lost) { lost = true; const response = await route.fetch(); expect(response.status()).toBe(201); await route.abort("failed"); } else await route.continue(); });
  await vp.getByRole("button", { name: "Enviar consulta" }).click(); await expect(vp.getByRole("alert")).toBeVisible(); await expect(vp.getByLabel("Nombre de contacto")).toHaveValue(name); await vp.getByRole("button", { name: "Enviar consulta" }).click(); await expect(vp.getByRole("heading", { name: "Solicitud recibida." })).toBeVisible(); await expect(vp.getByText("<script>Texto de una consulta sintética</script>", { exact: true })).toHaveCount(0);
  await page.goto(origin + "/publicacion"); const card = page.locator("article").filter({ has: page.getByRole("heading", { name, exact: true }) }); await expect(card).toBeVisible(); await expect(card.getByText("<script>Texto de una consulta sintética</script>", { exact: true })).toBeVisible();
  const contacts = await (await page.request.get(origin + "/api/v1/contacts?size=100")).json(); expect(contacts.filter((c: { name: string }) => c.name === name)).toHaveLength(1); expect(contacts.find((c: { name: string }) => c.name === name).deliveryState).toBe("PENDING"); expect((await vp.request.get(origin + "/api/v1/contacts")).status()).toBe(401);
  await visitor.close();
});
