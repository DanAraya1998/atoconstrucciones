import { test, expect, type Page } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { taskInput, type Schedule, type TaskInput } from "../../src/features/scheduling/types";

test.skip(process.env.ATO_E2E_ONBOARDING!=="1","Requiere realm/backend/PostgreSQL aislados y opt-in explícito");
test.use({baseURL:"http://localhost:3302",actionTimeout:15000});
test.setTimeout(90000);
async function command(page:Page,path:string,body:unknown,version?:number){
  const csrf=await (await page.request.get("/api/v1/session/csrf")).json();
  const response=await page.request.post(path,{data:body,headers:{[csrf.headerName]:csrf.token,"Idempotency-Key":randomUUID(),...(version?{"If-Match":`"${version}"`}:{})}});
  expect(response.status(),await response.text()).toBeLessThan(300);return response.json();
}
test("proveedor sin obra y reprogramación por formulario/resize persisten en PostgreSQL",async({page})=>{
  const accounts=await readFile(resolve(process.cwd(),"../.m0-local/accounts.txt"),"utf8"),password=accounts.split("\n").find(line=>line.startsWith("gerente@example.invalid: "))!.split(": ")[1];
  await page.goto("/auth/login");await page.locator("#username").fill("gerente");await page.locator("#password").fill(password);await page.locator("#kc-login").click();await expect(page.getByRole("link",{name:"Usuarios y accesos",exact:true})).toBeVisible();
  const suffix=randomUUID().slice(0,8);await page.goto("/cartera");await page.getByRole("button",{name:"Proveedores",exact:true}).click();await page.locator("summary").filter({hasText:"Registrar proveedor"}).click();
  const supplierForm=page.locator("details").filter({has:page.locator("summary").filter({hasText:"Registrar proveedor"})}).locator("form");
  await supplierForm.getByLabel("Nombre o razón social").fill(`Proveedor PLAN ${suffix}`);await supplierForm.getByLabel("Correo de contacto").fill(`supplier-${suffix}@example.invalid`);await supplierForm.getByLabel("Teléfono de contacto").fill("20000000");await supplierForm.getByRole("combobox",{name:"Categoría de proveedor",exact:true}).selectOption("HARDWARE_STORE");await supplierForm.getByRole("button",{name:"Crear registro"}).click();await expect(supplierForm.getByRole("status")).toHaveText("Cambio guardado.");
  const suppliers=await (await page.request.get("/api/v1/suppliers?supplierCategory=HARDWARE_STORE&size=100")).json(),supplier=suppliers.items.find((p:{name:string})=>p.name===`Proveedor PLAN ${suffix}`);expect(supplier).toBeTruthy();expect(supplier.supplierCategory).toBe("HARDWARE_STORE");expect(await (await page.request.get(`/api/v1/suppliers/${supplier.id}/members`)).json()).toEqual([]);
  const client=await command(page,"/api/v1/clients",{name:`Cliente PLAN ${suffix}`,email:`client-${suffix}@example.invalid`,phone:"20000000",category:"",active:true});
  const project=await command(page,"/api/v1/projects",{code:`PLAN-${suffix.toUpperCase()}`,name:`Obra PLAN ${suffix}`,clientId:client.id,location:"Ubicación sintética",contactEmail:`project-${suffix}@example.invalid`,startsOn:"2026-10-01",estimatedEndsOn:"2026-12-01"});
  const a:TaskInput={id:randomUUID(),parentId:null,kind:"LEAF",title:"Predecesora PLAN",startDate:"2026-10-01",endExclusive:"2026-10-05",state:"PLANNED",administrative:false,clientVisible:false,extraId:null,supplierIds:[]},b:TaskInput={...a,id:randomUUID(),title:"Sucesora PLAN",startDate:"2026-10-06",endExclusive:"2026-10-09"};
  const saved:Schedule=await command(page,`/api/v1/projects/${project.id}/schedule/changes`,{baseRevision:1,tasks:[a,b],dependencies:[{predecessorId:a.id,successorId:b.id,kind:"FS",lagDays:1}],reason:"Plan inicial de prueba"},1);
  await page.goto(`/obras/${project.id}`);
  async function shift(title:string){await page.getByRole("button",{name:`Reprogramar ${title}`,exact:true}).click();const form=page.getByRole("form",{name:`Reprogramar ${title}`,exact:true});await form.getByLabel("Cómo reprogramar").selectOption("shift");await form.getByLabel("Desplazamiento en días").fill("1");await form.getByRole("button",{name:"Preparar reprogramación"}).click();}
  await shift(a.title);await page.getByLabel("Motivo del cambio del cronograma").fill("Cambio de disponibilidad");await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText(/Conflicto FS:.*2026-10-07/)).toBeVisible();expect((await (await page.request.get(`/api/v1/projects/${project.id}/schedule`)).json()).revision).toBe(saved.revision);
  await shift(b.title);await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText("Cronograma guardado.",{exact:true})).toBeVisible();await page.reload();
  let persisted:Schedule=await (await page.request.get(`/api/v1/projects/${project.id}/schedule`)).json();expect(persisted.tasks.find(t=>t.id===a.id)).toMatchObject({startDate:"2026-10-02",endExclusive:"2026-10-06"});expect(persisted.tasks.find(t=>t.id===b.id)).toMatchObject({startDate:"2026-10-07",endExclusive:"2026-10-10"});
  await page.locator("summary").filter({hasText:"Abrir Gantt editable"}).click();const bar=page.getByTestId("schedule-gantt").locator(`.wx-bar[data-id=":${b.id}"]`);await bar.scrollIntoViewIfNeeded();const box=(await bar.boundingBox())!;await page.mouse.move(box.x+box.width-15,box.y+5);await page.mouse.down();await page.mouse.move(box.x+box.width+35,box.y+5,{steps:8});await page.mouse.up();await expect(page.getByText("Cambios pendientes de guardar.",{exact:true})).toBeVisible();await page.getByLabel("Motivo del cambio del cronograma").fill("Ampliar duración de sucesora");await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText("Cronograma guardado.",{exact:true})).toBeVisible();
  persisted=await (await page.request.get(`/api/v1/projects/${project.id}/schedule`)).json();expect(persisted.tasks.find(t=>t.id===b.id)).toMatchObject({startDate:"2026-10-07",endExclusive:"2026-10-11"});expect(persisted.tasks.map(taskInput)).toHaveLength(2);expect(persisted.revision).toBe(saved.revision+2);
});
