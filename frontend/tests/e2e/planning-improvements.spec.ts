import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";
import type { Schedule, TaskInput } from "../../src/features/scheduling/types";

test.use({actionTimeout:10000});
const manager={id:randomUUID(),email:"manager@example.invalid",roles:["GERENTE"],mfa:false,authenticatedAt:new Date().toISOString()};
async function planning(page:Page,count=2,fail?:"conflict"|"dependency") {
  const projectId=randomUUID(),tasks:Schedule["tasks"]=Array.from({length:count},(_,i)=>({id:randomUUID(),parentId:null,kind:"LEAF",title:`Actividad ${i+1}`,startDate:"2026-10-01",endExclusive:"2026-10-05",state:"PLANNED",administrative:false,clientVisible:false,extraId:null,supplierIds:[],verifiedPercent:"0",verifiedOn:null,verifiedAt:null}));
  let schedule:Schedule={projectId,revision:1,tasks,dependencies:[],baselines:[],reports:[],progressTaskIds:tasks.map(t=>t.id)};
  const saves:{body:{tasks:TaskInput[];baseRevision:number;reason:string};key:string}[]=[];
  await page.route("**/api/v1/**",async route=>{
    const r=route.request(),path=new URL(r.url()).pathname;
    if(path==="/api/v1/session")return route.fulfill({json:manager});
    if(path==="/api/v1/session/csrf")return route.fulfill({json:{headerName:"X-CSRF-TOKEN",token:"synthetic"}});
    if(path===`/api/v1/projects/${projectId}`)return route.fulfill({json:{id:projectId,code:"PLAN-001",name:"Obra de planificación",clientId:randomUUID(),location:"Ubicación sintética",contactEmail:"obra@example.invalid",startsOn:"2026-10-01",estimatedEndsOn:"2026-12-01",state:"BORRADOR",version:1}});
    if(path.endsWith("/schedule/changes")){
      expect(r.headers()["x-csrf-token"]).toBe("synthetic");expect(r.headers()["if-match"]).toBe('"1"');const body=r.postDataJSON();saves.push({body,key:r.headers()["idempotency-key"]});
      if(fail==="conflict"){schedule={...schedule,revision:2};return route.fulfill({status:412,contentType:"application/problem+json",json:{code:"VERSION_MISMATCH",detail:"El cronograma cambió."}});}
      if(fail==="dependency")return route.fulfill({status:422,contentType:"application/problem+json",json:{code:"VALIDATION_FAILED",detail:"Conflicto FS: «Actividad 2» debe iniciar el 2026-10-05 o después por su dependencia de «Actividad 1»."}});
      schedule={...schedule,revision:2,tasks:body.tasks.map((t:TaskInput)=>({...t,verifiedPercent:"0",verifiedOn:null,verifiedAt:null}))};return route.fulfill({json:schedule});
    }
    if(path.endsWith("/schedule"))return route.fulfill({json:schedule});
    if(path.endsWith("/members"))return route.fulfill({json:[]});
    if(["/documents","/extras","/suppliers","/clients","/member-accounts"].some(s=>path.endsWith(s)))return route.fulfill({json:{items:[],page:0,size:20,hasMore:false}});
    return route.fulfill({status:404,json:{code:"NOT_FOUND"}});
  });
  await page.goto(`/obras/${projectId}`);await expect(page.getByRole("heading",{name:"Cronograma de obra",exact:true})).toBeVisible();
  return {projectId,tasks,saves};
}

test("proveedor: registrar sin obra, categorizar, filtrar y editar desde móvil",async({page})=>{
  const entries:{id:string;kind:string;name:string;email:string;phone:string;category:string;supplierCategory:string;active:boolean;version:number}[]=[];
  const posted:unknown[]=[];const filters:string[]=[];
  await page.route("**/api/v1/**",async route=>{
    const r=route.request(),url=new URL(r.url()),path=url.pathname;
    if(path==="/api/v1/session")return route.fulfill({json:manager});
    if(path==="/api/v1/session/csrf")return route.fulfill({json:{headerName:"X-CSRF-TOKEN",token:"synthetic"}});
    if(path==="/api/v1/suppliers"&&r.method()==="POST"){const body=r.postDataJSON();posted.push(body);expect(body.projectId).toBeUndefined();entries.push({...body,id:randomUUID(),kind:"SUPPLIER",version:1});return route.fulfill({status:201,json:entries.at(-1)});}
    if(path==="/api/v1/suppliers"){const filter=url.searchParams.get("supplierCategory");filters.push(filter??"");return route.fulfill({json:{items:entries.filter(p=>!filter||p.supplierCategory===filter),page:Number(url.searchParams.get("page")),size:20}});}
    if(r.method()==="PATCH"){expect(r.headers()["if-match"]).toBe('"1"');Object.assign(entries[0],r.postDataJSON(),{version:2});return route.fulfill({json:entries[0]});}
    return route.fulfill({json:{items:[],page:0,size:20,hasMore:false}});
  });
  await page.setViewportSize({width:390,height:844});await page.goto("/cartera");await page.getByRole("button",{name:"Proveedores",exact:true}).click();
  await expect(page.getByText(/aunque todavía no tengan una obra asignada/)).toBeVisible();await page.locator("summary").filter({hasText:"Registrar proveedor"}).click();
  const form=page.locator("details").filter({has:page.locator("summary").filter({hasText:"Registrar proveedor"})}).locator("form");
  await form.getByLabel("Nombre o razón social").fill("Electricista de prueba");await form.getByLabel("Correo de contacto").fill("supplier@example.invalid");await form.getByLabel("Teléfono de contacto").fill("20000000");await form.getByRole("combobox",{name:"Categoría de proveedor",exact:true}).selectOption("ELECTRICIAN");await form.getByLabel("Especialidad o detalle (opcional)").fill("Instalaciones residenciales");await form.getByRole("button",{name:"Crear registro"}).focus();await page.keyboard.press("Enter");
  await expect(form.getByRole("status")).toHaveText("Cambio guardado.");expect(posted).toHaveLength(1);expect(posted[0]).toMatchObject({supplierCategory:"ELECTRICIAN",category:"Instalaciones residenciales"});await expect(page.getByRole("heading",{name:"Electricista de prueba"})).toBeVisible();
  await page.getByLabel("Filtrar por categoría de proveedor").selectOption("PLUMBER");await expect(page.getByText("No hay proveedores accesibles en esta categoría.")).toBeVisible();
  await page.getByLabel("Filtrar por categoría de proveedor").selectOption("ELECTRICIAN");await expect(page.getByRole("heading",{name:"Electricista de prueba"})).toBeVisible();expect(filters).toContain("ELECTRICIAN");
  await page.locator("summary").filter({hasText:"Editar contacto"}).click();const edit=page.locator("form").filter({has:page.getByRole("button",{name:"Guardar contacto"})});await edit.getByRole("combobox",{name:"Categoría de proveedor",exact:true}).selectOption("FINISHES");await edit.getByRole("button",{name:"Guardar contacto"}).click();await expect(page.getByText("No hay proveedores accesibles en esta categoría.")).toBeVisible();
  await page.getByLabel("Filtrar por categoría de proveedor").selectOption("FINISHES");await expect(page.getByRole("heading",{name:"Electricista de prueba"})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
});

test("reprogramar por fechas, duración y desplazamiento; persistir el lote por teclado",async({page})=>{
  const {saves}=await planning(page);await page.setViewportSize({width:390,height:844});await page.getByRole("button",{name:"Reprogramar Actividad 1",exact:true}).click();
  let form=page.getByRole("form",{name:"Reprogramar Actividad 1",exact:true});await form.getByLabel("Nuevo inicio").fill("2026-10-30");await expect(form.getByLabel("Duración en días calendario")).toHaveValue("4");await expect(form.getByLabel("Último día de trabajo")).toHaveValue("2026-11-02");await form.getByLabel("Duración en días calendario").fill("6");await expect(form.getByLabel("Último día de trabajo")).toHaveValue("2026-11-04");await form.getByRole("button",{name:"Preparar reprogramación"}).click();
  await page.getByRole("button",{name:"Reprogramar Actividad 1",exact:true}).click();form=page.getByRole("form",{name:"Reprogramar Actividad 1",exact:true});await form.getByLabel("Cómo reprogramar").selectOption("shift");await form.getByLabel("Desplazamiento en días").fill("-2");await expect(form.getByRole("status")).toContainText("2026-10-28 → 2026-11-02 · 6 días");await form.getByRole("button",{name:"Preparar reprogramación"}).focus();await page.keyboard.press("Enter");
  await page.getByLabel("Motivo del cambio del cronograma").fill("Disponibilidad del proveedor");await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText("Cronograma guardado.",{exact:true})).toBeVisible();expect(saves).toHaveLength(1);expect(saves[0].body.tasks[0]).toMatchObject({startDate:"2026-10-28",endExclusive:"2026-11-03"});expect(saves[0].body.tasks[1]).toMatchObject({startDate:"2026-10-01",endExclusive:"2026-10-05"});
  await page.reload();await page.getByRole("button",{name:"Reprogramar Actividad 1",exact:true}).click();await expect(page.getByLabel("Nuevo inicio")).toHaveValue("2026-10-28");await expect(page.getByLabel("Duración en días calendario")).toHaveValue("6");expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
});

test("Gantt: altura por filas, vista ampliada, Escape y arrastre conservan fechas",async({page})=>{
  await page.setViewportSize({width:1440,height:1050});const {tasks,saves}=await planning(page,16);await page.locator("summary").filter({hasText:"Abrir Gantt editable"}).click();
  const chart=page.getByTestId("schedule-gantt");await expect(chart.getByRole("gridcell",{name:"Actividad 16",exact:true})).toBeVisible();expect((await chart.boundingBox())!.height).toBeGreaterThan(750);
  await page.getByRole("button",{name:"Ampliar Gantt"}).click();const dialog=page.getByRole("dialog",{name:"Vista ampliada del cronograma"});await expect(dialog).toBeVisible();expect((await dialog.boundingBox())!.height).toBeGreaterThan(1000);await expect(dialog.getByRole("gridcell",{name:"Actividad 16",exact:true})).toBeVisible();await page.keyboard.press("Escape");await expect(dialog).not.toBeVisible();await expect(page.getByRole("button",{name:"Ampliar Gantt"})).toBeFocused();
  const bar=chart.locator(`.wx-bar[data-id=":${tasks[0].id}"]`);await bar.scrollIntoViewIfNeeded();const box=(await bar.boundingBox())!;await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();await page.mouse.move(box.x+box.width/2+100,box.y+box.height/2,{steps:8});await page.mouse.up();await expect(page.getByText("Cambios pendientes de guardar.",{exact:true})).toBeVisible();
  await page.getByLabel("Motivo del cambio del cronograma").fill("Reprogramación visual");await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText("Cronograma guardado.",{exact:true})).toBeVisible();expect(saves[0].body.tasks[0]).toMatchObject({startDate:"2026-10-03",endExclusive:"2026-10-07"});await chart.screenshot({path:"test-results/gantt-height.png"});
  await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.getByRole("button",{name:"Ampliar Gantt"}).click();await expect(dialog).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await dialog.getByRole("button",{name:"Cerrar vista ampliada"}).click();await expect(dialog).not.toBeVisible();
});

for(const fail of ["dependency","conflict"] as const)test(`reprogramación: ${fail} conserva borrador y explica el rechazo`,async({page})=>{
  await planning(page,2,fail);await page.getByRole("button",{name:"Reprogramar Actividad 2",exact:true}).click();const form=page.getByRole("form",{name:"Reprogramar Actividad 2",exact:true});await form.getByLabel("Nuevo inicio").fill("2026-09-30");await form.getByRole("button",{name:"Preparar reprogramación"}).click();await page.getByLabel("Motivo del cambio del cronograma").fill("Propuesta a revisar");await page.getByRole("button",{name:"Guardar lote del cronograma"}).click();await expect(page.getByText(fail==="dependency"?/Conflicto FS:/ : /Otro usuario cambió el cronograma/)).toBeVisible();await page.getByRole("button",{name:"Reprogramar Actividad 2",exact:true}).click();await expect(page.getByLabel("Nuevo inicio")).toHaveValue("2026-09-30");if(fail==="conflict")await expect(page.getByRole("button",{name:"Guardar lote del cronograma"})).toBeDisabled();
});
