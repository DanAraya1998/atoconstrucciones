"use client";
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { components } from "@/lib/api/schema";
import { apiRequest } from "@/lib/api/transport";
import { useSession } from "@/features/projects/shared";
import { Button } from "@/components/ui/button";
import { roleLabel } from "./account-picker";
import { clearInvitation, usePendingInvitation } from "./invitation-store";
export function InviteAccount({role,initialEmail=""}:{role?:string;initialEmail?:string}) {
  const [invitation,setInvitation]=useState<components["schemas"]["Invitation"]>(),[link,setLink]=useState(""),[notice,setNotice]=useState(""),[busy,setBusy]=useState(false);
  return <details className="rounded-xl border border-border bg-white p-5"><summary className="cursor-pointer font-semibold">Invitar a una persona</summary>
    <form className="mt-5 grid gap-4" onSubmit={async(e)=>{e.preventDefault();const data=new FormData(e.currentTarget);setBusy(true);setNotice("");try{const created=await apiRequest<components["schemas"]["Invitation"]>("/api/v1/invitations",{method:"POST",body:JSON.stringify({email:String(data.get("email")).trim(),role:role??data.get("role")})});setInvitation(created);setLink(`${window.location.origin}/invitacion#${new URLSearchParams({id:created.id,token:created.token,expiresAt:created.expiresAt})}`);setNotice("Invitación creada. Comparte el enlace con la persona invitada.");}catch(error){setNotice(error instanceof Error?error.message:"No se pudo crear la invitación.");}finally{setBusy(false);}}}>
      <label>Correo de la persona<input name="email" type="email" defaultValue={initialEmail} required maxLength={254}/></label>
      {role?<p>Rol que aceptará: {roleLabel[role]}.</p>:<label>Rol de la invitación<select name="role" defaultValue="CLIENTE">{["CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA","ADMIN"].map(r=><option key={r} value={r}>{roleLabel[r]}</option>)}</select></label>}
      <p className="text-sm">La persona debe abrir el enlace e iniciar sesión con el correo invitado. Después de aceptar, podrás buscarla y vincularla a esta ficha.</p>
      <Button disabled={busy}>{busy?"Creando invitación…":"Crear invitación"}</Button>
    </form>
    {notice&&<p role="status" className="mt-4 rounded-lg bg-muted p-3">{notice}</p>}
    {invitation&&<div className="mt-4 flex flex-wrap items-center gap-4"><Button type="button" variant="outline" onClick={async()=>{try{await navigator.clipboard.writeText(link);setNotice("Enlace copiado.");}catch{setNotice("Copia la dirección del enlace «Ver invitación» con el menú del navegador.");}}}>Copiar enlace</Button><a href={link} target="_blank" rel="noopener" className="underline">Ver invitación</a><p className="text-sm">Vigente hasta {new Intl.DateTimeFormat("es-CR",{timeZone:"America/Costa_Rica",dateStyle:"short",timeStyle:"short"}).format(new Date(invitation.expiresAt))}.</p></div>}
  </details>;
}
export function InvitationAcceptance({onAccepted}:{onAccepted?:(message:string)=>void}={}){
  const {pending,invalid}=usePendingInvitation(),session=useSession(),cache=useQueryClient();const [notice,setNotice]=useState(""),[busy,setBusy]=useState(false);
  if(notice&&!pending)return <p role="status" className="rounded-lg bg-muted p-4">{notice}</p>;
  if(invalid)return <div role="alert"><p>El enlace de invitación está incompleto o ha caducado. Solicita un enlace nuevo a gerencia.</p><Button type="button" variant="outline" onClick={clearInvitation}>Descartar invitación</Button></div>;
  if(!pending)return <p>Abre el enlace compartido por gerencia para aceptar tu invitación.</p>;
  if(session.isPending)return <p role="status">Consultando tu cuenta…</p>;
  if(!session.data)return <div className="space-y-4"><p>Inicia sesión con el correo invitado para aceptar el acceso.</p><Button asChild><a href="/auth/login">Ingresar para aceptar</a></Button><Button variant="outline" onClick={clearInvitation}>Descartar invitación</Button></div>;
  return <div className="space-y-4"><p>Aceptarás la invitación con la cuenta <strong>{session.data.email}</strong>.</p><Button disabled={busy} onClick={async()=>{setBusy(true);setNotice("");try{await apiRequest<void>(`/api/v1/invitations/${pending.id}/accept`,{method:"POST",body:JSON.stringify({token:pending.token})});const accepted="Invitación aceptada. Tu cuenta ya tiene el rol concedido por gerencia.";onAccepted?.(accepted);clearInvitation();await cache.invalidateQueries({queryKey:["session"]});setNotice(accepted);}catch{setNotice("No se pudo aceptar. Comprueba que usas el correo invitado y que el enlace sigue vigente; también puede haberse utilizado.");}finally{setBusy(false);}}}>{busy?"Aceptando…":"Aceptar invitación"}</Button><Button variant="outline" disabled={busy} onClick={clearInvitation}>Descartar invitación</Button>{notice&&<p role="alert">{notice}</p>}</div>;
}
