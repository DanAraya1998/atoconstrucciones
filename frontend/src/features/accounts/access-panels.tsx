"use client";
import { useState } from "react";
import type { components } from "@/lib/api/schema";
import type { Actor } from "@/lib/api/transport";
import { ActionForm, useData, send, text, type Party, type Project } from "@/features/projects/shared";
import { Button } from "@/components/ui/button";
import { AccountPicker, ConfirmAccount, accountLabel, type Account } from "./account-picker";
import { DirectAccountForm } from "./onboarding";
import Link from "next/link";
function Pager({page,hasMore,onPage}:{page:number;hasMore:boolean;onPage:(page:number)=>void}){return <div className="flex flex-wrap items-center gap-3"><Button variant="outline" disabled={page===0} onClick={()=>onPage(page-1)}>Accesos anteriores</Button><span>Página {page+1}</span><Button variant="outline" disabled={!hasMore} onClick={()=>onPage(page+1)}>Más accesos</Button></div>;}
export function PartyAccessPanel({user,party,kind}:{user:Actor;party:Party;kind:string}){const [open,setOpen]=useState(false);return <details className="rounded-xl border border-border p-5" onToggle={e=>setOpen(e.currentTarget.open)}><summary className="cursor-pointer font-semibold">Gestionar acceso de cuentas</summary>{open&&<PartyAccess user={user} party={party} kind={kind}/>}</details>;}
function PartyAccess({user,party,kind}:{user:Actor;party:Party;kind:string}){
  const [page,setPage]=useState(0),[account,setAccount]=useState<Account|null>(null),path=`/api/v1/${kind}/${party.id}`;
  const data=useData<components["schemas"]["PartyMemberAccountPage"]>(user,`${path}/member-accounts?page=${page}&size=20`),role=kind==="clients"?"CLIENTE":"PROVEEDOR";
  return <div className="mt-5 space-y-5"><h3 className="font-semibold">Cuentas vinculadas</h3>{data.isPending&&<p role="status">Consultando accesos…</p>}{data.error&&<p role="alert">No se pudieron consultar los accesos.</p>}{data.data?.items.length===0&&<p>No hay cuentas vinculadas.</p>}
    {data.data?.items.map(m=><article key={m.account.id} className="rounded-lg border border-border p-4 space-y-3"><p className="break-words font-semibold">{accountLabel(m.account)}</p><p>{m.active?"Acceso concedido":"Acceso revocado"}{!m.account.enabled?" · Cuenta desactivada":""}</p>{m.active&&<ActionForm title={`Revocar acceso de ${m.account.email}`} submit="Confirmar revocación" run={(_,key)=>send(`${path}/members`,"PUT",{userId:m.account.id,active:false},key,party.version)}><p>Retirarás el acceso de {accountLabel(m.account)} a {party.name}. Los documentos y registros anteriores se conservan.</p></ActionForm>}</article>)}
    {data.data&&(page>0||data.data.hasMore)&&<Pager page={page} hasMore={data.data.hasMore} onPage={setPage}/>}
    <ActionForm title="Vincular una cuenta" submit="Conceder acceso" run={(d,key)=>send(`${path}/members`,"PUT",{userId:text(d,"userId"),active:true},key,party.version)}><AccountPicker user={user} name="userId" role={role} initialEmail={party.email??""} onSelect={setAccount}/><ConfirmAccount account={account}/></ActionForm>
    <DirectAccountForm user={user} party={party} role={role}/>
  </div>;
}
export function ProjectAccessPanel({user,project}:{user:Actor;project:Project}){const [open,setOpen]=useState(false);return <details className="rounded-xl border border-border p-5" onToggle={e=>setOpen(e.currentTarget.open)}><summary className="cursor-pointer font-semibold">Gestionar acceso a la obra</summary>{open&&<ProjectAccess user={user} project={project}/>}</details>;}
const capacityLabel:Record<string,string>={CLIENT:"Cliente",OPERATIONS:"Operación administrativa",SUPPLIER:"Proveedor"};
function ProjectAccess({user,project}:{user:Actor;project:Project}){
  const [page,setPage]=useState(0),[capacity,setCapacity]=useState("CLIENT"),[supplier,setSupplier]=useState(""),[account,setAccount]=useState<Account|null>(null),path=`/api/v1/projects/${project.id}`;
  const data=useData<components["schemas"]["ProjectMemberAccountPage"]>(user,`${path}/member-accounts?page=${page}&size=20`),suppliers=useData<{items:Party[]}>(capacity==="SUPPLIER"?user:undefined,"/api/v1/suppliers?size=100");
  const endpoint=`${path}/account-candidates?${new URLSearchParams({capacity,...(capacity==="SUPPLIER"?{supplierId:supplier}:{})})}`;
  return <div className="mt-5 space-y-5"><h3 className="font-semibold">Cuentas con acceso a esta obra</h3>{data.isPending&&<p role="status">Consultando accesos…</p>}{data.error&&<p role="alert">No se pudieron consultar los accesos.</p>}{data.data?.items.length===0&&<p>No hay cuentas vinculadas.</p>}
    {data.data?.items.map(m=><article key={m.account.id+m.capacity} className="rounded-lg border border-border p-4 space-y-3"><p className="break-words font-semibold">{accountLabel(m.account)}</p><p>{capacityLabel[m.capacity]}{m.supplierName?` · ${m.supplierName}`:""}{m.representative?" · Representante contractual":""} · {m.active?"Acceso concedido":"Acceso revocado"}{!m.account.enabled?" · Cuenta desactivada":""}</p>{m.active&&<ActionForm title={`Revocar acceso de ${m.account.email} como ${capacityLabel[m.capacity]}`} submit="Confirmar revocación" run={(_,key)=>send(`${path}/members`,"PUT",{userId:m.account.id,capacity:m.capacity,supplierId:m.supplierId,representative:m.representative,active:false},key,project.version)}><p>Retirarás el acceso de {accountLabel(m.account)} a esta obra. Se conserva el historial.</p></ActionForm>}</article>)}
    {data.data&&(page>0||data.data.hasMore)&&<Pager page={page} hasMore={data.data.hasMore} onPage={setPage}/>}
    <ActionForm title="Vincular una cuenta a la obra" submit="Conceder acceso a la obra" run={(d,key)=>send(`${path}/members`,"PUT",{userId:text(d,"userId"),capacity,supplierId:capacity==="SUPPLIER"?supplier:null,representative:capacity==="CLIENT"&&d.get("representative")==="on",active:true},key,project.version)}>
      <label>Tipo de acceso<select value={capacity} onChange={e=>{setCapacity(e.target.value);setSupplier("");setAccount(null);}}>{Object.entries(capacityLabel).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
      {capacity==="SUPPLIER"&&<label>Proveedor vinculado<select required value={supplier} onChange={e=>{setSupplier(e.target.value);setAccount(null);}}><option value="">Selecciona un proveedor</option>{suppliers.data?.items.filter(s=>s.active).map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></label>}
      {capacity!=="SUPPLIER"||supplier?<AccountPicker key={capacity+supplier} user={user} name="userId" endpoint={endpoint} onSelect={setAccount}/>:<p>Selecciona el proveedor para buscar sus cuentas.</p>}
      {capacity==="CLIENT"&&<><p className="text-sm">Se muestran cuentas vinculadas al cliente contratante en cartera.</p><label className="flex items-start gap-3"><input name="representative" type="checkbox" className="!w-5"/><span>Puede representar al cliente en acuerdos contractuales.</span></label></>}
      <ConfirmAccount account={account}/>
    </ActionForm>
    <p className="text-sm">Para habilitar una cuenta nueva o aprobar una solicitud, abre <Link className="underline" href="/usuarios">Usuarios y accesos</Link>. Allí puedes asignar la cartera y las obras en una sola operación.</p>
  </div>;
}
