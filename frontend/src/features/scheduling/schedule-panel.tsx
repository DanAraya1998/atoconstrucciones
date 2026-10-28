"use client";
import dynamic from "next/dynamic";
import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { ApiError, apiRequest, type Actor } from "@/lib/api/transport";
import { useData, text, type Party, type Extra, Field, ActionForm, send, type DocumentInfo } from "@/features/projects/shared";
import { ProgressPanel } from "@/features/progress/progress-panel";
import { Choice, today } from "@/features/finance/shared";
import { taskInput, type Schedule, type TaskInput, type Dependency, type AssignedTasks } from "./types";
import { RescheduleTask } from "./reschedule-task";
import { supplierCategories } from "@/features/parties/supplier-categories";
const Gantt=dynamic(()=>import("./schedule-gantt"),{ssr:false,loading:()=> <p role="status">Cargando vista Gantt…</p>});

export function SchedulePanel({user,projectId}:{user:Actor;projectId:string}) {
  const data=useData<Schedule>(user,`/api/v1/projects/${projectId}/schedule`);
  if(data.isPending)return <p role="status">Cargando planificación…</p>;
  if(!data.data)return <p role="alert">No se pudo consultar el cronograma.</p>;
  return <Editor user={user} projectId={projectId} server={data.data}/>;
}
type Draft={revision:number;tasks:TaskInput[];dependencies:Dependency[]};
function Editor({user,projectId,server}:{user:Actor;projectId:string;server:Schedule}) {
  const [local,setLocal]=useState<Draft|null>(null),[editId,setEditId]=useState<string|null>(null),[notice,setNotice]=useState(""),[busy,setBusy]=useState(false),[conflict,setConflict]=useState(false),[reason,setReason]=useState("");
  const retry=useRef<{body:string;key:string}|null>(null),cache=useQueryClient();
  const [rescheduleId,setRescheduleId]=useState<string|null>(null);
  const draft=local??{revision:server.revision,tasks:server.tasks.map(taskInput),dependencies:server.dependencies};
  const parties=useData<{items:Party[]}>(user,"/api/v1/suppliers?size=100"),members=useData<{supplierId:string|null;active:boolean;capacity:string}[]>(user,`/api/v1/projects/${projectId}/members`),extras=useData<{items:Extra[]}>(user,`/api/v1/projects/${projectId}/extras`);
  const assigned=new Set(members.data?.filter(m=>m.capacity==="SUPPLIER"&&m.active).map(m=>m.supplierId));
  const suppliers=parties.data?.items.filter(p=>p.active&&assigned.has(p.id))??[];
  const editing=draft.tasks.find(t=>t.id===editId);
  const rescheduling=draft.tasks.find(t=>t.id===rescheduleId);
  function stage(next:Draft){setLocal(next);setNotice("Cambios pendientes de guardar.");}
  async function save(){
    const body=JSON.stringify({baseRevision:draft.revision,tasks:draft.tasks,dependencies:draft.dependencies,reason});
    if(retry.current?.body!==body)retry.current={body,key:crypto.randomUUID()};setBusy(true);setNotice("");
    try{await apiRequest<Schedule>(`/api/v1/projects/${projectId}/schedule/changes`,{method:"POST",body,headers:{"Idempotency-Key":retry.current.key,"If-Match":`"${draft.revision}"`}});retry.current=null;await cache.invalidateQueries({queryKey:["m1"]});setLocal(null);setEditId(null);setConflict(false);setReason("");setNotice("Cronograma guardado.");}
    catch(e){if(e instanceof ApiError&&e.status===412){setConflict(true);await cache.invalidateQueries({queryKey:["m1"]});setNotice("Otro usuario cambió el cronograma. Tus cambios siguen pendientes. Revisa la nueva revisión antes de guardar.");}else setNotice(e instanceof Error?e.message:"No se pudo guardar.");}
    finally{setBusy(false);}
  }
  const options=draft.tasks.filter(t=>t.kind!=="SUMMARY"&&t.state!=="CANCELLED");
  return <section aria-label="Planificación de obra" className="space-y-5"><div className="panel space-y-4"><h2 className="text-2xl font-semibold">Cronograma de obra</h2><p>Revisión {draft.revision} · {local?"Cambios pendientes":"Guardado"}</p><p className="text-sm text-slate-600">Días calendario. El fin de una hoja es exclusivo. Retirar conserva su historia; los cambios de alcance y fechas requieren revisar los pesos.</p>
    {notice&&<p role="status" className="rounded-lg bg-muted p-3">{notice}</p>}
    {conflict&&<Button variant="outline" onClick={()=>{setLocal(null);setConflict(false);setEditId(null);retry.current=null;setNotice(`Se cargó la revisión ${server.revision}. Revisa y vuelve a preparar tus cambios.`);}}>Descartar mis cambios y cargar revisión vigente</Button>}
    <fieldset disabled={busy} className="min-w-0 space-y-4">
    <ul className="space-y-3">{draft.tasks.map(t=><li key={t.id} className="rounded-lg border border-border p-3"><p className="font-semibold">{t.title} · {({LEAF:"Tarea",SUMMARY:"Etapa",MILESTONE:"Hito"})[t.kind]} · {({PLANNED:"Planificada",IN_PROGRESS:"En ejecución",BLOCKED:"Bloqueada",COMPLETED:"Completada",CANCELLED:"Retirada"})[t.state]}</p><p className="mt-2 text-sm">{t.parentId?`Etapa: ${draft.tasks.find(p=>p.id===t.parentId)?.title}`:"Sin etapa"} · {t.startDate??"Inicio agregado"} → {t.endExclusive??"Fin agregado"}{t.administrative?" · Administración interna":""}</p><div className="mt-3 flex flex-wrap gap-3"><Button variant="outline" onClick={()=>{setEditId(t.id);setRescheduleId(null);}}>Editar {t.title}</Button>{t.kind!=="SUMMARY"&&t.state!=="CANCELLED"&&<Button variant="outline" onClick={()=>{setRescheduleId(t.id);setEditId(null);}}>Reprogramar {t.title}</Button>}</div></li>)}</ul>
    {rescheduling&&<RescheduleTask key={`${rescheduling.id}:${rescheduling.startDate}:${rescheduling.endExclusive}`} task={rescheduling} cancel={()=>setRescheduleId(null)} prepare={(start,end)=>{stage({...draft,tasks:draft.tasks.map(t=>t.id===rescheduling.id?{...t,startDate:start,endExclusive:end}:t)});setRescheduleId(null);}}/>}
    {draft.tasks.length===0&&<p>No hay tareas. Prepara entregables y etapas.</p>}
    <Button variant="outline" onClick={()=>{setEditId("new");setRescheduleId(null);}}>Agregar tarea, etapa o hito</Button>
    {editId&&<form key={editId} className="grid gap-4 rounded-xl border border-border p-4" onSubmit={e=>{e.preventDefault();const data=new FormData(e.currentTarget),kind=text(data,"kind") as TaskInput["kind"];
      const task:TaskInput={id:editing?.id??crypto.randomUUID(),parentId:text(data,"parent")||null,kind,title:text(data,"title"),startDate:kind==="SUMMARY"?null:text(data,"start"),endExclusive:kind==="SUMMARY"?null:kind==="MILESTONE"?text(data,"start"):text(data,"end"),state:text(data,"state") as TaskInput["state"],administrative:data.get("administrative")==="on",clientVisible:kind==="MILESTONE"&&data.get("clientVisible")==="on",extraId:text(data,"extra")||null,supplierIds:data.getAll("suppliers").map(String)};
      stage({...draft,tasks:editing?draft.tasks.map(t=>t.id===editing.id?task:t):[...draft.tasks,task]});setEditId(null);
    }}><h3 className="text-lg font-semibold">{editing?"Editar entregable":"Nuevo entregable"}</h3><Field label="Nombre del entregable" name="title" value={editing?.title} maxLength={160}/><label>Tipo de entregable<select name="kind" defaultValue={editing?.kind??"LEAF"} disabled={Boolean(editing)}><option value="LEAF">Tarea hoja</option><option value="SUMMARY">Etapa</option><option value="MILESTONE">Hito</option></select></label>{editing&&<input type="hidden" name="kind" value={editing.kind}/>}
    <label>Etapa superior<select name="parent" defaultValue={editing?.parentId??""}><option value="">Sin etapa</option>{draft.tasks.filter(t=>t.kind==="SUMMARY"&&t.id!==editing?.id).map(t=><option key={t.id} value={t.id}>{t.title}</option>)}</select></label>
    <Field label="Fecha de inicio o hito" name="start" type="date" required={false} value={editing?.startDate??today()}/><Field label="Fin exclusivo (solo hoja)" name="end" type="date" required={false} value={editing?.endExclusive??today()}/>
    <label>Estado del entregable<select name="state" defaultValue={editing?.state??"PLANNED"}><option value="PLANNED">Planificada</option><option value="IN_PROGRESS">En ejecución</option><option value="BLOCKED">Bloqueada</option><option value="COMPLETED">Completada con verificación</option><option value="CANCELLED">Retirada</option></select></label>
    <label>Extra vinculado<select name="extra" defaultValue={editing?.extraId??""} disabled={Boolean(editing)}><option value="">Contrato base</option>{extras.data?.items.map(x=><option key={x.id} value={x.id}>{x.scope} · {x.state}</option>)}</select></label>{editing&&<input name="extra" type="hidden" value={editing.extraId??""}/>}
    <fieldset><legend>Proveedores asignados</legend><p className="text-xs mb-3">Selecciona proveedores de cartera con una membresía vigente en esta obra.</p>{suppliers.map(p=><label key={p.id} className="flex items-center gap-3 mb-2"><input className="w-4" type="checkbox" name="suppliers" value={p.id} defaultChecked={editing?.supplierIds.includes(p.id)}/>{p.name}{p.supplierCategory?` · ${supplierCategories[p.supplierCategory]}`:""}</label>)}</fieldset>
    <label className="flex items-center gap-3"><input className="w-4" type="checkbox" name="administrative" defaultChecked={editing?.administrative}/>Actividad administrativa, excluida del avance</label><label className="flex items-center gap-3"><input className="w-4" type="checkbox" name="clientVisible" defaultChecked={editing?.clientVisible}/>Compartir hito entregable con cliente</label><Button>Preparar cambio de tarea</Button></form>}
    <details className="rounded-lg border border-border p-4"><summary className="cursor-pointer font-semibold">Dependencias fin a inicio</summary><ul className="mt-4 space-y-3">{draft.dependencies.map((d,i)=><li key={i}><p>{draft.tasks.find(t=>t.id===d.predecessorId)?.title} → {draft.tasks.find(t=>t.id===d.successorId)?.title} · {d.lagDays} días</p><button className="underline text-sm" onClick={()=>stage({...draft,dependencies:draft.dependencies.filter((_,j)=>i!==j)})}>Quitar dependencia</button></li>)}</ul><form className="mt-4 grid gap-4" onSubmit={e=>{e.preventDefault();const d=new FormData(e.currentTarget);stage({...draft,dependencies:[...draft.dependencies,{predecessorId:text(d,"from"),successorId:text(d,"to"),kind:"FS",lagDays:Number(text(d,"lag"))}]});}}><Choice label="Tarea predecesora" name="from" items={options.map(t=>({id:t.id,name:t.title}))}/><Choice label="Tarea sucesora" name="to" items={options.map(t=>({id:t.id,name:t.title}))}/><Field label="Desfase en días calendario" name="lag" type="number" min="0" max="3650" value="0"/><Button variant="outline">Preparar dependencia</Button></form></details>
    <details className="min-w-0 overflow-hidden"><summary className="cursor-pointer font-semibold">Abrir Gantt editable</summary><p className="my-4 text-sm">Arrastrar prepara fechas locales. Guarda el lote para validarlo. También puedes editar cada tarea por formulario.</p><Gantt tasks={draft.tasks} dependencies={draft.dependencies} readonly={busy} change={(id,start,end)=>stage({...draft,tasks:draft.tasks.map(t=>t.id===id?{...t,startDate:start,endExclusive:t.kind==="MILESTONE"?start:end}:t)})}/></details>
    <label>Motivo del cambio del cronograma<input value={reason} onChange={e=>setReason(e.target.value)} maxLength={1000}/></label><div className="flex flex-wrap gap-3"><Button disabled={!local||busy||conflict||!reason.trim()} onClick={()=>void save()}>Guardar lote del cronograma</Button><Button variant="outline" disabled={!local||busy} onClick={()=>{setLocal(null);setEditId(null);setConflict(false);retry.current=null;setNotice("Cambios locales descartados.");}}>Deshacer cambios pendientes</Button></div></fieldset></div>
    <ProgressPanel user={user} projectId={projectId} schedule={server}/>
  </section>;
}
export function SupplierSchedulePanel({user,projectId}:{user:Actor;projectId:string}) {
  const data=useData<AssignedTasks>(user,`/api/v1/projects/${projectId}/schedule/tasks`),documents=useData<{items:DocumentInfo[]}>(user,`/api/v1/projects/${projectId}/documents`);
  if(!data.data)return <p role="status">Consultando tareas asignadas…</p>;
  const clean=documents.data?.items.filter(d=>d.state==="CLEAN").map(d=>({id:d.id,name:d.filename}))??[];
  return <section className="panel space-y-5"><h2 className="text-xl font-semibold">Mis tareas asignadas</h2>{data.data.items.map(t=><article key={t.id} className="rounded-lg border border-border p-3 space-y-3"><h3 className="font-semibold">{t.title}</h3><p>{t.startDate} → {t.endExclusive} · verificado {t.verifiedPercent}%</p>{t.earliestStart&&<p>Restricción de inicio: {t.earliestStart}.</p>}<ActionForm title={`Reportar avance de ${t.title}`} submit="Reportar medición" run={(d,k)=>send(`/api/v1/projects/${projectId}/schedule/tasks/${t.id}/progress-reports`,"POST",{percent:text(d,"percent"),observedOn:text(d,"observed"),evidenceId:text(d,"evidence"),note:text(d,"note")},k,data.data!.revision)}><Field label="Porcentaje reportado" name="percent" type="number" min="0" max="100" step="0.0001"/><Field label="Fecha de medición" name="observed" type="date" value={today()}/><Choice label="Evidencia del avance" name="evidence" items={clean}/><Field label="Descripción de la medición" name="note" maxLength={1000}/></ActionForm></article>)}{data.data.items.length===0&&<p>No tienes tareas vigentes asignadas.</p>}<ul>{data.data.reports.map(r=><li key={r.id}>Medición propia: {r.reportedPercent}% · {r.verifiedPercent!==null?`Verificado ${r.verifiedPercent}%`:"Pendiente de verificación"}</li>)}</ul></section>;
}
