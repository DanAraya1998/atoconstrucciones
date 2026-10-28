import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
test.skip(!process.env.ATO_E2E_ACCOUNTS, "Requiere el backend aislado y Keycloak con las cuentas sintéticas");
const origin=process.env.ATO_E2E_ACCOUNTS_ORIGIN??"http://localhost:3000";
async function login(page:Page,name:string){
  // Captura la respuesta del login antes de su redirección: nunca visita el servidor habitual.
  // El intercambio del código mantiene el redirect_uri registrado en el proveedor.
  if(origin!=="http://localhost:3000")await page.route("**/login-actions/authenticate**",async route=>{
    const response=await route.fetch({maxRedirects:0}),headers=response.headers();
    if(headers.location?.startsWith("http://localhost:3000/login/oauth2/code/ato"))headers.location=headers.location.replace("http://localhost:3000",origin);
    await route.fulfill({response,headers});
  });
  const password=readFileSync(resolve("../.m0-local/accounts.txt"),"utf8").split("\n").find(l=>l.startsWith(`${name}@example.invalid: `))!.split(": ")[1];
  await page.goto(origin+"/auth/login");await page.locator("#username").fill(name);await page.locator("#password").fill(password);await page.locator("#kc-login").click();await expect(page).toHaveURL(/\/fundamentos$/);return (await (await page.request.get(origin+"/api/v1/session")).json());
}
async function command(page:Page,path:string,data:unknown){const csrf=await (await page.request.get(origin+"/api/v1/session/csrf")).json();const response=await page.request.post(origin+path,{data,headers:{[csrf.headerName]:csrf.token,"Idempotency-Key":randomUUID()}});expect(response.status()).toBeLessThan(300);return response.json();}
async function open(page:Page,title:string){const s=page.locator("summary").filter({hasText:title}).first();await s.click();return s.locator("..");}
test("cuentas real: aceptar enlace anterior, vincular y revocar acceso a obra",async({page,browser})=>{
  test.setTimeout(120000);await login(page,"gerente");const context=await browser.newContext(),clientPage=await context.newPage(),client=await login(clientPage,"cliente-a");
  const suffix=randomUUID().slice(0,8),party=await command(page,"/api/v1/clients",{name:`Cliente acceso ${suffix}`,email:client.email,phone:"20000000",category:"",active:true});
  // Las invitaciones ya emitidas siguen admitidas; el recorrido principal ahora es alta/aprobación.
  const invitation=await command(page,"/api/v1/invitations",{email:client.email,role:"CLIENTE"});
  const link=origin+"/invitacion#"+new URLSearchParams({id:invitation.id,token:invitation.token,expiresAt:invitation.expiresAt});
  await page.goto(origin+"/cartera");const card=page.locator("section").filter({has:page.getByRole("heading",{name:party.name,exact:true})});await card.locator("summary").filter({hasText:"Gestionar acceso de cuentas"}).click();
  await clientPage.goto(link);await clientPage.getByRole("button",{name:"Aceptar invitación",exact:true}).click();await expect(clientPage.getByRole("status")).toContainText("Invitación aceptada.");
  const grantSummary=card.locator("summary").filter({hasText:"Vincular una cuenta"});await grantSummary.click();const grant=grantSummary.locator("..");await grant.getByRole("button",{name:"Buscar cuentas"}).click();const select=grant.getByRole("combobox",{name:"Cuenta a vincular"});await expect(select.locator("option")).toHaveCount(2);const label=await select.locator("option").nth(1).innerText();await select.selectOption({label});await grant.getByRole("checkbox").check();await grant.getByRole("button",{name:"Conceder acceso",exact:true}).click();await expect(grant.getByRole("status")).toHaveText("Cambio guardado.");expect(await page.locator("body").innerText()).not.toContain(client.id);
  const project=await command(page,"/api/v1/projects",{code:`AC-${suffix.toUpperCase()}`,name:`Obra acceso ${suffix}`,clientId:party.id,location:"Ubicación demo",contactEmail:"project@example.invalid",startsOn:"2026-10-01",estimatedEndsOn:"2026-12-01"});
  await page.goto(origin+`/obras/${project.id}`);await open(page,"Gestionar acceso a la obra");const projectGrant=await open(page,"Vincular una cuenta a la obra");await projectGrant.getByLabel("Buscar cuenta por nombre o correo").fill(client.email);await projectGrant.getByRole("button",{name:"Buscar cuentas"}).click();await projectGrant.getByRole("combobox",{name:"Cuenta a vincular"}).selectOption({label});await projectGrant.getByRole("checkbox",{name:/Puede representar/}).check();await projectGrant.getByRole("checkbox",{name:/Confirmo que/}).check();await projectGrant.getByRole("button",{name:"Conceder acceso a la obra"}).click();await expect(projectGrant.getByRole("status")).toHaveText("Cambio guardado.");expect((await clientPage.request.get(origin+`/api/v1/projects/${project.id}/client-portal`)).status()).toBe(200);
  const revoke=await open(page,`Revocar acceso de ${client.email} como Cliente`);await revoke.getByRole("button",{name:"Confirmar revocación"}).click();await expect(page.getByText(/Cliente.*Acceso revocado/)).toBeVisible();expect((await clientPage.request.get(origin+`/api/v1/projects/${project.id}/client-portal`)).status()).toBe(404);await context.close();
});
