"use client";
import Link from "next/link";
import { useState } from "react";
import type { Actor } from "@/lib/api/transport";
import { civilDate, Shell, ActionForm, Field, useData, text, send, type Project, type Party } from "./shared";
import { normalizeProjectCode, ProjectCodeField } from "./project-code-field";
export function ProjectsWorkspace() { return <Shell title="Obras">{user => <Projects user={user} />}</Shell>; }
function Projects({ user }: { user: Actor }) {
  const [page,setPage] = useState(0);
  const projects = useData<{ items: Project[] }>(user,`/api/v1/projects?page=${page}&size=20`);
  const clients = useData<{ items: Party[] }>(user,"/api/v1/clients?size=100");
  const canPrepare = user.roles.includes("GERENTE") || user.roles.includes("ASISTENTE_ADMINISTRATIVA");
  return <><p className="text-slate-600">Consulta las obras a las que tienes acceso y revisa su contrato y evidencias.</p>
    {projects.isPending ? <p role="status">Cargando obras…</p> : projects.isError ? <p role="alert">No se pudieron consultar las obras.</p> : <div className="grid gap-4 md:grid-cols-2">{projects.data?.items.map(p => <Link className="panel block hover:border-primary focus-visible:outline-2" key={p.id} href={`/obras/${p.id}`}><p className="text-sm text-slate-600">{p.code} · {p.state}</p><h2 className="mt-2 text-xl font-semibold">{p.name}</h2><p className="mt-3 text-sm">{p.location || "Ubicación pendiente"}</p><p className="mt-2 text-sm">{civilDate(p.startsOn)} — {civilDate(p.estimatedEndsOn)}</p></Link>)}</div>}
    {projects.data?.items.length === 0 && <p>Aún no tienes obras asignadas.</p>}<div className="flex gap-4"><button disabled={page===0} onClick={() => setPage(page-1)}>Anterior</button><span>Página {page+1}</span><button disabled={(projects.data?.items.length ?? 0)<20} onClick={() => setPage(page+1)}>Siguiente</button></div>
    {canPrepare && <ActionForm title="Preparar una obra" submit="Crear borrador" normalizeData={data => data.set("code", normalizeProjectCode(text(data,"code")))} run={(data,key) => send("/api/v1/projects","POST",{code:text(data,"code"),name:text(data,"name"),clientId:text(data,"clientId"),location:text(data,"location"),contactEmail:text(data,"contactEmail"),startsOn:text(data,"startsOn"),estimatedEndsOn:text(data,"endsOn"),latitude:null,longitude:null},key)}>
      <ProjectCodeField /><Field label="Nombre de la obra" name="name" maxLength={160} /><label>Cliente contratante<select name="clientId" required><option value="">Seleccione un cliente</option>{clients.data?.items.filter(c=>c.active).map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label><Field label="Ubicación descriptiva" name="location" required={false} maxLength={500} /><Field label="Correo de contacto de la obra" name="contactEmail" type="email" /><Field label="Inicio estimado" name="startsOn" type="date" /><Field label="Final estimado" name="endsOn" type="date" />
    </ActionForm>}
  </>;
}
