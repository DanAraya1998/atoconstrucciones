import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";

test("alta directa: confirmar de nuevo al cambiar permisos y reintentar conserva la intención", async ({ page }) => {
  const manager = randomUUID();let attempts = 0;const keys: string[] = [];
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if(path === "/api/v1/session")return route.fulfill({json:{id:manager,email:"gerente@example.invalid",roles:["GERENTE"],mfa:false,authenticatedAt:new Date().toISOString()}});
    if(path === "/api/v1/session/csrf")return route.fulfill({json:{headerName:"X-CSRF-TOKEN",token:"synthetic"}});
    if(path === "/api/v1/accounts/capabilities")return route.fulfill({json:{directAvailable:true}});
    if(path === "/api/v1/accounts"){
      attempts++;keys.push(route.request().headers()["idempotency-key"]);expect(route.request().headers()["x-csrf-token"]).toBe("synthetic");
      expect(route.request().postDataJSON()).toEqual({displayName:"Persona demo",email:"persona@example.invalid",access:{role:"ASISTENTE_ADMINISTRATIVA",partyId:null,partyVersion:null,projects:[]}});
      if(attempts===1)return route.fulfill({status:503,contentType:"application/problem+json",json:{status:503,code:"IDENTITY_PROVIDER_UNAVAILABLE",detail:"Servicio temporalmente no disponible."}});
      return route.fulfill({status:201,json:{id:randomUUID(),state:"APPROVED",activationState:"PENDING"}});
    }
    return route.fulfill({json:{items:[],page:0,size:20,hasMore:false}});
  });
  await page.setViewportSize({width:390,height:844});await page.goto("/usuarios");await page.locator("summary").filter({hasText:"Crear una cuenta"}).click();
  const form=page.locator("details").filter({has:page.locator("summary").filter({hasText:"Crear una cuenta"})}).first();
  await form.getByLabel("Nombre de la persona",{exact:true}).fill("Persona demo");await form.getByLabel("Correo de la persona",{exact:true}).fill("persona@example.invalid");
  const confirm=form.getByRole("checkbox",{name:/Confirmo la identidad/});await confirm.check();await form.getByLabel("Perfil de acceso").selectOption("ASISTENTE_ADMINISTRATIVA");await expect(confirm).not.toBeChecked();
  await form.getByRole("button",{name:"Crear y habilitar",exact:true}).click();expect(attempts).toBe(0);
  await confirm.check();await form.getByRole("button",{name:"Crear y habilitar",exact:true}).focus();await page.keyboard.press("Enter");await expect(form.getByRole("status")).toContainText("Servicio temporalmente no disponible.");
  await form.getByRole("button",{name:"Crear y habilitar",exact:true}).click();await expect(form.getByRole("status")).toContainText("Cambio guardado.");expect(keys).toHaveLength(2);expect(keys[0]).toBe(keys[1]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
});

test("registro rechazado: estado propio recuperable y navegación empresarial ausente",async({page})=>{
  const id=randomUUID();let attempts=0;
  await page.route("**/api/v1/**",async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==="/api/v1/session")return route.fulfill({json:{id,email:"persona@example.invalid",roles:[],mfa:false,authenticatedAt:new Date().toISOString()}});
    if(path==="/api/v1/accounts/me"){
      attempts++;if(attempts===1)return route.fulfill({status:503,contentType:"application/problem+json",json:{status:503,code:"UNAVAILABLE",detail:"Servicio no disponible"}});
      return route.fulfill({json:{id,email:"persona@example.invalid",displayName:"Persona demo",roles:[],state:"REJECTED",emailVerified:true,version:2,activationState:"NONE",createdAt:new Date().toISOString()}});
    }
    throw new Error("La cuenta pendiente no debe consultar datos empresariales");
  });
  await page.goto("/obras");await expect(page.getByRole("alert").filter({hasText:"No se pudo consultar"})).toBeVisible();await page.getByRole("button",{name:"Consultar estado",exact:true}).click();await expect(page.getByRole("heading",{name:"Tu solicitud no fue aprobada"})).toBeVisible();await expect(page.getByRole("link",{name:"Obras",exact:true})).toHaveCount(0);
});
