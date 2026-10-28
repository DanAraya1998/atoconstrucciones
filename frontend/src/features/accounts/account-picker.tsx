"use client";
import { useId, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { components } from "@/lib/api/schema";
import { apiRequest, type Actor } from "@/lib/api/transport";
import { Button } from "@/components/ui/button";
export type Account = components["schemas"]["AccountSummary"];
export const roleLabel: Record<string,string> = { GERENTE:"Gerencia", CLIENTE:"Cliente", PROVEEDOR:"Proveedor", ASISTENTE_ADMINISTRATIVA:"Asistente administrativa", ADMIN:"Administrador técnico" };
export function accountLabel(account: Account) { return account.displayName ? `${account.displayName} · ${account.email}` : account.email; }
export function AccountPicker({ user, name, role, initialEmail = "", endpoint = "/api/v1/accounts/search", onSelect }: { user:Actor; name:string; role?:string; initialEmail?:string; endpoint?:string; onSelect?:(account:Account|null)=>void }) {
  const id=useId(), [draft,setDraft]=useState(initialEmail), [query,setQuery]=useState(""), [page,setPage]=useState(0), [selected,setSelected]=useState<Account|null>(null);
  const data=useQuery({queryKey:["m1",user.id,"accounts",endpoint,query,role,page],queryFn:()=>apiRequest<components["schemas"]["AccountPage"]>(endpoint,{method:"POST",body:JSON.stringify({query,role:role??null,page,size:20})}),enabled:query.length>=2,retry:false});
  function search(){const value=draft.trim();if(value.length<2)return;if(value===query&&page===0)void data.refetch();setQuery(value);setPage(0);setSelected(null);onSelect?.(null);}
  return <div className="grid min-w-0 gap-3 rounded-lg border border-border p-4">
    <label htmlFor={`${id}-search`}>Buscar cuenta por nombre o correo<input id={`${id}-search`} value={draft} maxLength={254} autoComplete="off" onChange={(e)=>{setDraft(e.target.value);setSelected(null);onSelect?.(null);}} onKeyDown={(e)=>{if(e.key==="Enter"){e.preventDefault();search();}}}/></label>
    <Button type="button" variant="outline" disabled={draft.trim().length<2} onClick={search}>Buscar cuentas</Button>
    <p id={`${id}-help`} className="text-sm text-slate-600">{role?`Selecciona una cuenta activa con rol ${roleLabel[role]}. `:"Selecciona la persona que recibirá acceso. "}Busca con al menos dos caracteres.</p>
    {query&&data.isPending&&<p role="status">Buscando cuentas…</p>}{data.error&&<p role="alert">No se pudieron consultar las cuentas. Reintenta la búsqueda.</p>}
    {query&&data.data?.items.length===0&&<p role="status">No encontramos cuentas habilitadas para esta búsqueda. Puedes crear una invitación desde esta ficha.</p>}
    <label htmlFor={`${id}-account`} className="min-w-0">Cuenta a vincular<select className="min-w-0 max-w-full" id={`${id}-account`} name={name} required value={selected?.id??""} aria-describedby={`${id}-help`} onChange={(e)=>{const account=data.data?.items.find(a=>a.id===e.target.value)??null;setSelected(account);onSelect?.(account);}}>
      <option value="">Selecciona una cuenta</option>{data.data?.items.map(a=><option key={a.id} value={a.id}>{accountLabel(a)}</option>)}
    </select></label>
    {data.data&&(page>0||data.data.hasMore)&&<div className="flex flex-wrap items-center gap-3"><Button type="button" variant="outline" disabled={page===0} onClick={()=>{setPage(page-1);setSelected(null);onSelect?.(null);}}>Cuentas anteriores</Button><span>Página {page+1}</span><Button type="button" variant="outline" disabled={!data.data.hasMore} onClick={()=>{setPage(page+1);setSelected(null);onSelect?.(null);}}>Más cuentas</Button></div>}
    {selected&&<p className="break-words rounded-lg bg-muted p-3">Cuenta seleccionada: <strong>{accountLabel(selected)}</strong> · {selected.roles.map(r=>roleLabel[r]).join(", ")||"Pendiente de invitación"}</p>}
  </div>;
}
export function ConfirmAccount({account}:{account:Account|null}) {return <label key={account?.id??"none"} className="flex min-w-0 items-start gap-3"><input name="confirmAccount" type="checkbox" required disabled={!account} className="!w-5 shrink-0"/><span className="min-w-0 break-words">Confirmo que {account?accountLabel(account):"la cuenta seleccionada"} es la persona que recibirá este acceso.</span></label>;}
