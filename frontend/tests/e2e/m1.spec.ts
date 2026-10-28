import { test, expect, type Page, type APIRequestContext } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID, createHash } from "node:crypto";

test.skip(!process.env.ATO_E2E_M1, "Requiere PostgreSQL + Keycloak + Spring + Google Drive de pruebas y ClamAV");
const origin = "http://localhost:3000";
async function login(page: Page, name: string) {
  const accounts = readFileSync(resolve("../.m0-local/accounts.txt"), "utf8");
  const password = accounts.split("\n").find(line => line.startsWith(`${name}@example.invalid: `))!.split(": ")[1];
  await page.goto(origin + "/auth/login");
  await page.locator("#username").fill(name); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/fundamentos$/);
  const response = await page.request.get(origin + "/api/v1/session"); expect(response.status()).toBe(200); return response.json();
}
async function command(request: APIRequestContext, path: string, method: string, data?: unknown, version?: number) {
  const csrf = await (await request.get(origin + "/api/v1/session/csrf")).json();
  const response = await request.fetch(origin + path, { method, data, headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID(), ...(version ? { "If-Match": `"${version}"` } : {}) } });
  expect(response.status(), `${method} ${path}: ${await response.text()}`).toBeLessThan(300);
  return response.status() === 204 ? undefined : response.json();
}
async function form(page: Page, summary: string) {
  const details = page.locator("details").filter({ has: page.locator("summary").filter({ hasText: summary }) }).first();
  await details.locator("summary").first().click(); return details.locator("form").first();
}

test("M1 real: cartera, obra, contrato, evidencia limpia, extra, aceptación y revocación", async ({ page, browser }) => {
  test.setTimeout(120000);
  await login(page,"gerente"); const clientContext = await browser.newContext(); const clientPage = await clientContext.newPage();
  const client = await login(clientPage,"cliente-a");
  await command(page.request,`/api/v1/users/${client.id}/roles`,"PUT",{ roles:["CLIENTE"] });
  const suffix = randomUUID().slice(0,8).toUpperCase(), partyName = `Cliente E2E ${suffix}`, code = `E2E-${suffix}`;
  await page.goto(origin+"/cartera"); const partyForm=await form(page,"Registrar cliente");
  await partyForm.getByLabel("Nombre o razón social").fill(partyName); await partyForm.getByLabel("Correo de contacto").fill("cliente@example.invalid"); await partyForm.getByLabel("Teléfono de contacto").fill("+506 2000 0000"); await partyForm.getByRole("button",{name:"Crear registro"}).click();
  await expect(page.getByRole("heading",{name:partyName})).toBeVisible();
  const parties=await (await page.request.get(origin+"/api/v1/clients?size=100")).json(); const party=parties.items.find((p:{name:string})=>p.name===partyName);
  await command(page.request,`/api/v1/clients/${party.id}/members`,"PUT",{userId:client.id,active:true},party.version);
  await page.goto(origin+"/obras"); const projectForm=await form(page,"Preparar una obra");
  await projectForm.getByLabel("Código de obra").fill(code); await projectForm.getByLabel("Nombre de la obra").fill(`Obra E2E ${suffix}`); await projectForm.getByLabel("Cliente contratante").selectOption(party.id); await projectForm.getByLabel("Ubicación descriptiva").fill("Ubicación sintética de ensayo"); await projectForm.getByLabel("Correo de contacto de la obra").fill("obra@example.invalid"); await projectForm.getByLabel("Inicio estimado").fill("2026-10-01"); await projectForm.getByLabel("Final estimado").fill("2026-12-01"); await projectForm.getByRole("button",{name:"Crear borrador"}).click();
  const link=page.getByRole("link").filter({hasText:`Obra E2E ${suffix}`}); await expect(link).toBeVisible(); const projectId=(await link.getAttribute("href"))!.split("/").at(-1)!; await link.click();
  let project=await (await page.request.get(origin+`/api/v1/projects/${projectId}`)).json();
  await command(page.request,`/api/v1/projects/${projectId}/members`,"PUT",{userId:client.id,capacity:"CLIENT",supplierId:null,representative:true,active:true},project.version); await page.reload();
  const uploadForm=await form(page,"Adjuntar evidencia"); const file=Buffer.from("<?xml version='1.0'?><evidencia>Acuerdo sintético E2E</evidencia>");
  await uploadForm.getByLabel("Archivo",{exact:true}).setInputFiles({name:"acuerdo.xml",mimeType:"application/xml",buffer:file}); await uploadForm.getByLabel("Clase documental").selectOption("CONTRACT"); await uploadForm.getByRole("button",{name:"Cargar archivo privado"}).click();
  await expect(page.getByText("Limpio · Interno")).toBeVisible({timeout:30000});
  const docs=await (await page.request.get(origin+`/api/v1/projects/${projectId}/documents`)).json(); const evidence=docs.items[0];
  expect(evidence.sha256).toBe(createHash("sha256").update(file).digest("hex"));
  await command(page.request,`/api/v1/documents/${evidence.id}/sharing`,"PUT",{audience:"CLIENT",recipientId:null,reason:"Acuerdo para el representante"},evidence.version);
  const baseForm=await form(page,"Preparar contrato base"); await baseForm.getByLabel("Número de contrato").fill(`CON-${suffix}`); await baseForm.getByLabel("Alcance",{exact:true}).fill("Construcción sintética de prueba"); await baseForm.getByLabel("Neto en CRC",{exact:true}).fill("1000000.00"); await baseForm.getByLabel("Impuestos en CRC",{exact:true}).fill("130000.00"); await baseForm.getByRole("button",{name:"Guardar contrato"}).click();
  await expect(page.getByText(`CON-${suffix} · Borrador`)).toBeVisible();
  const formalize=await form(page,"Formalizar contrato base"); await formalize.getByLabel("Identificador de evidencia limpia").fill(evidence.id); await formalize.getByLabel("Motivo de formalización").fill("Acuerdo documentado sintético"); await formalize.getByRole("button",{name:"Formalizar",exact:true}).click();
  await expect(page.getByText(`CON-${suffix} · Formalizado`)).toBeVisible();
  const activation=await form(page,"Cambiar estado de la obra"); await activation.getByLabel("Motivo",{exact:true}).fill("Inicio autorizado"); await activation.getByRole("button",{name:"Aplicar estado"}).click(); await expect(page.getByText(new RegExp(`${code} · ACTIVO`))).toBeVisible();
  const extraForm=await form(page,"Solicitar trabajo adicional"); await extraForm.getByLabel("Alcance solicitado").fill("Añadir pared de prueba"); await extraForm.getByRole("button",{name:"Enviar solicitud"}).click(); await expect(page.getByRole("heading",{name:"Añadir pared de prueba"})).toBeVisible();
  const revisionForm=await form(page,"Cotizar nueva revisión"); await revisionForm.getByLabel("Alcance cotizado").fill("Alcance cotizado verificado"); await revisionForm.getByLabel("Precio neto al cliente en CRC").fill("200000.10"); await revisionForm.getByLabel("Impuestos en CRC").fill("26000.01"); await revisionForm.getByLabel("Cambio de plazo en días").fill("3"); await revisionForm.getByRole("button",{name:"Guardar cotización"}).click(); await expect(page.getByText("PENDIENTE_APROBACION · Revisión 1")).toBeVisible();
  const contractPath=origin+`/api/v1/projects/${projectId}/contract`; expect((await (await page.request.get(contractPath)).json()).currentTotal.amount).toBe("1130000.00");
  await clientPage.goto(origin+`/obras/${projectId}`); const acceptance=await form(clientPage,"Aceptar esta cotización"); await acceptance.getByLabel("Evidencia de aceptación").selectOption(evidence.id); await acceptance.getByLabel("Referencia de mi aceptación").fill("Acepto alcance y precio de la versión uno"); await acceptance.getByRole("button",{name:"Registrar mi aceptación"}).click(); await expect(clientPage.getByText("Aceptación registrada")).toBeVisible();
  await page.reload(); const approval=await form(page,"Formalizar aprobación del extra"); await approval.getByLabel("Identificador de evidencia limpia").fill(evidence.id); await approval.getByLabel("Motivo o referencia de aceptación").fill("Gerencia formaliza el acuerdo"); await approval.getByRole("button",{name:"Aprobar extra"}).click(); await expect(page.getByText("APROBADO · Revisión 1")).toBeVisible();
  expect((await (await page.request.get(contractPath)).json()).currentTotal.amount).toBe("1356000.11");
  await page.screenshot({path:"test-results/m1-desktop.png",fullPage:true});
  await clientPage.setViewportSize({width:390,height:844});await clientPage.reload();await expect(clientPage.getByRole("heading",{name:"Ficha de obra"})).toBeVisible();
  expect(await clientPage.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);await clientPage.keyboard.press("Tab");expect(await clientPage.evaluate(()=>document.activeElement?.tagName)).toBe("A");await clientPage.screenshot({path:"test-results/m1-mobile.png",fullPage:true});
  await clientPage.goto(origin+"/privacidad");
  const privacyForm=await form(clientPage,"Solicitar acceso, rectificación o retiro");
  const privacyDetails=`Consulta sintética de privacidad ${suffix}`;
  await privacyForm.getByLabel("Tipo").selectOption("ACCESS");await privacyForm.getByLabel("Detalle",{exact:true}).fill(privacyDetails);await privacyForm.getByRole("button",{name:"Enviar solicitud"}).click();
  await expect(clientPage.getByText(privacyDetails,{exact:true})).toBeVisible();
  await page.goto(origin+"/privacidad");
  const privacyRequest=page.locator("article").filter({hasText:privacyDetails});
  await expect(privacyRequest).toBeVisible();await privacyRequest.locator("summary").click();
  await privacyRequest.getByLabel("Respuesta y medidas realizadas").fill("Respuesta sintética del ensayo");await privacyRequest.getByRole("button",{name:"Resolver solicitud"}).click();await expect(privacyRequest.getByText("ACCESS · RESOLVED")).toBeVisible();
  project=await (await page.request.get(origin+`/api/v1/projects/${projectId}`)).json(); await command(page.request,`/api/v1/projects/${projectId}/members`,"PUT",{userId:client.id,capacity:"CLIENT",supplierId:null,representative:true,active:false},project.version);
  expect((await clientPage.request.get(origin+`/api/v1/documents/${evidence.id}/content`)).status()).toBe(404); await clientPage.goto(origin+`/obras/${projectId}`); await expect(clientPage.getByText("Esta obra no está disponible para tu cuenta.")).toBeVisible(); await clientContext.close();
});
