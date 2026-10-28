"use client";
import { useState } from "react";
import { Shell, ActionForm, Field, useData, text, send, type Party, Reauthenticate } from "@/features/projects/shared";
import type { Actor } from "@/lib/api/transport";
import { PartyAccessPanel } from "@/features/accounts/access-panels";
import { supplierCategories } from "./supplier-categories";
export function PartiesWorkspace() { return <Shell title="Clientes y proveedores">{user => <Parties user={user} />}</Shell>; }
function Parties({ user }: { user: Actor }) {
  const [kind, setKind] = useState("clients"), [page, setPage] = useState(0), [category,setCategory]=useState("");
  const parties = useData<{ items: Party[] }>(user, `/api/v1/${kind}?page=${page}&size=20${kind==="suppliers"&&category?`&supplierCategory=${encodeURIComponent(category)}`:""}`);
  const manager = user.roles.includes("GERENTE");
  function input(data: FormData) { return { name: text(data,"name"), email: text(data,"email"), phone: text(data,"phone"), category: text(data,"category"), active: text(data,"active") !== "false", ...(kind==="suppliers"?{supplierCategory:text(data,"supplierCategory")}: {}) }; }
  const fields = (party?: Party) => <><Field label="Nombre o razón social" name="name" value={party?.name} maxLength={160} /><Field label="Correo de contacto" name="email" type="email" value={party?.email} maxLength={254} /><Field label="Teléfono de contacto" name="phone" value={party?.phone} maxLength={24} />{kind==="suppliers"&&<label>Categoría de proveedor<select name="supplierCategory" required defaultValue={party?.supplierCategory??(party?"OTHER":"")}><option value="" disabled>Selecciona una categoría</option>{Object.entries(supplierCategories).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>}<Field label={kind==="suppliers"?"Especialidad o detalle (opcional)":"Categoría"} name="category" value={party?.category} required={false} maxLength={100} /><label>Estado<select name="active" defaultValue={String(party?.active ?? true)}><option value="true">Activo</option><option value="false">Inactivo</option></select></label></>;
  return <><div className="flex gap-3" role="group" aria-label="Tipo de cartera"><button className="rounded-lg border px-4 py-2" aria-pressed={kind==="clients"} onClick={() => {setKind("clients");setPage(0);}}>Clientes</button><button className="rounded-lg border px-4 py-2" aria-pressed={kind==="suppliers"} onClick={() => {setKind("suppliers");setPage(0);}}>Proveedores</button></div>
    <Reauthenticate />
    {kind==="suppliers"&&<><p>Registra proveedores en tu cartera aunque todavía no tengan una obra asignada. La cuenta de usuario y los accesos a obras se habilitan por separado.</p><label>Filtrar por categoría de proveedor<select value={category} onChange={e=>{setCategory(e.target.value);setPage(0);}}><option value="">Todas las categorías</option>{Object.entries(supplierCategories).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label></>}
    {parties.isPending ? <p role="status">Cargando cartera…</p> : parties.isError ? <p role="alert">No se pudo consultar la cartera.</p> : <div className="grid gap-4 md:grid-cols-2">{parties.data?.items.map(party => <section className="panel min-w-0 space-y-4" key={party.id}><h2 className="break-words text-xl font-semibold">{party.name}</h2><p>{party.active ? "Activo" : "Inactivo"} · Revisión {party.version}</p>{party.email && <p className="break-all">{party.email} · {party.phone}</p>}
      {party.supplierCategory&&<p className="font-medium">{supplierCategories[party.supplierCategory]}</p>}{party.category&&<p>{party.category}</p>}
      {party.email && <ActionForm key={`${party.id}:${party.version}`} title="Editar contacto" submit="Guardar contacto" run={(data,key) => send(`/api/v1/${kind}/${party.id}`,"PATCH",input(data),key,party.version)}>{fields(party)}</ActionForm>}
      {manager && <PartyAccessPanel user={user} party={party} kind={kind}/>}
    </section>)}</div>}
    {parties.data?.items.length === 0 && <p>{kind==="suppliers"&&category?"No hay proveedores accesibles en esta categoría.":"Aún no hay registros accesibles."}</p>}<div className="flex gap-4"><button disabled={page===0} onClick={() => setPage(page-1)}>Anterior</button><span>Página {page+1}</span><button disabled={(parties.data?.items.length ?? 0)<20} onClick={() => setPage(page+1)}>Siguiente</button></div>
    {manager && <ActionForm key={kind} title={kind === "clients" ? "Registrar cliente" : "Registrar proveedor"} submit="Crear registro" run={(data,key) => send(`/api/v1/${kind}`,"POST",input(data),key)}>{fields()}</ActionForm>}
  </>;
}
