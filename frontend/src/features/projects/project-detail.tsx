"use client";
import { ProjectAccessPanel } from "@/features/accounts/access-panels";
import type { Actor } from "@/lib/api/transport";
import { civilDate, Shell, ActionForm, Field, Reauthenticate, useData, text, send, type Project, type Contract } from "./shared";
import { ContractPanel } from "@/features/contracts/contract-panel";
import { ExtrasPanel } from "@/features/extras/extras-panel";
import { DocumentsPanel } from "@/features/documents/documents-panel";
import { ProjectFinance } from "@/features/finance/project-finance";
import { SchedulePanel, SupplierSchedulePanel } from "@/features/scheduling/schedule-panel";
import { ClientProjectContent } from "@/features/client/client-workspace";
export function ProjectDetail({ id }: { id: string }) { return <Shell title="Ficha de obra">{user => <Detail user={user} id={id} />}</Shell>; }
function Detail({ user, id }: { user: Actor; id: string }) {
  if(user.roles.includes("CLIENTE")&&!user.roles.includes("GERENTE")&&!user.roles.includes("ASISTENTE_ADMINISTRATIVA"))return <ClientProjectContent user={user} id={id}/>;
  return <OperationalDetail user={user} id={id}/>;
}
function OperationalDetail({user,id}:{user:Actor;id:string}) {
  const data = useData<Project>(user,`/api/v1/projects/${id}`);
  const operational=user.roles.includes("GERENTE")||user.roles.includes("ASISTENTE_ADMINISTRATIVA");
  const members = useData<{userId:string;capacity:string;active:boolean;representative:boolean}[]>(operational?user:undefined,`/api/v1/projects/${id}/members`);
  const contract = useData<Contract>(operational?user:undefined,`/api/v1/projects/${id}/contract`);
  if (data.isPending) return <p role="status">Cargando obra…</p>;
  if (!data.data) return <p role="alert">Esta obra no está disponible para tu cuenta.</p>;
  const p = data.data, manager=user.roles.includes("GERENTE"), ops=manager||Boolean(members.data);
  return <><section className="panel"><p className="text-sm">{p.code} · {p.state} · Revisión {p.version}</p><h2 className="mt-2 text-2xl font-semibold">{p.name}</h2><p className="mt-4">{p.location || "Ubicación pendiente"}</p><p className="mt-2">{civilDate(p.startsOn)} — {civilDate(p.estimatedEndsOn)}</p>{p.contactEmail && <p className="mt-2 break-all">{p.contactEmail}</p>}</section>
    {ops && <ActionForm title="Editar ficha operativa" submit="Guardar ficha" run={(d,k)=>send(`/api/v1/projects/${id}`,"PATCH",{name:text(d,"name"),location:text(d,"location"),contactEmail:text(d,"email"),startsOn:text(d,"start"),estimatedEndsOn:text(d,"end"),latitude:text(d,"lat")?Number(text(d,"lat")):null,longitude:text(d,"lon")?Number(text(d,"lon")):null},k,p.version)}><Field label="Nombre" name="name" value={p.name} maxLength={160}/><Field label="Ubicación" name="location" value={p.location} required={false} maxLength={500}/><Field label="Correo de contacto" name="email" type="email" value={p.contactEmail}/><Field label="Inicio estimado" name="start" type="date" value={p.startsOn}/><Field label="Final estimado" name="end" type="date" value={p.estimatedEndsOn}/><Field label="Latitud" name="lat" type="number" required={false} min="-90" max="90" step="0.0000001" value={p.latitude?.toString()}/><Field label="Longitud" name="lon" type="number" required={false} min="-180" max="180" step="0.0000001" value={p.longitude?.toString()}/></ActionForm>}
    {manager && <><Reauthenticate/><ProjectAccessPanel user={user} project={p}/>
    <ActionForm title="Cambiar estado de la obra" submit="Aplicar estado" run={(d,k)=>send(`/api/v1/projects/${id}/${text(d,"action")}`,"POST",{reason:text(d,"reason")},k,p.version)}><label>Acción<select name="action"><option value="activate">Activar o reanudar</option><option value="suspend">Suspender</option><option value="cancel">Cancelar borrador</option></select></label><Field label="Motivo" name="reason" maxLength={1000}/><p className="text-sm">La activación requiere contrato formalizado y representante autorizado.</p></ActionForm></>}

    {(manager||contract.data||p.clientId) && <ContractPanel user={user} project={p} contract={contract.data} missing={contract.error} />}
    {(manager||p.clientId) && <ExtrasPanel user={user} project={p} operations={ops}/>}
    {(manager||p.clientId) && <ProjectFinance user={user} projectId={id} operations={ops}/>}
    {ops&&<SchedulePanel user={user} projectId={id}/>}
    {!ops&&user.roles.includes("PROVEEDOR")&&<SupplierSchedulePanel user={user} projectId={id}/>}
    <DocumentsPanel user={user} projectId={id}/>
  </>;
}
