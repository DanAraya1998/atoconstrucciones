"use client";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { apiRequest, type Actor } from "@/lib/api/transport";
import { ActionForm, Field, useData, text, send, type DocumentInfo } from "@/features/projects/shared";
export function DocumentsPanel({ user,projectId }: { user:Actor;projectId:string }) {
  const path=`/api/v1/projects/${projectId}/documents`, data=useData<{items:DocumentInfo[]}>(user,path), cache=useQueryClient();
  const pending=data.data?.items.some(d=>d.state==="QUARANTINED");
  useEffect(()=>{if(!pending)return;const timer=setInterval(()=>{void cache.invalidateQueries({queryKey:["m1",user.id,path]});},3000);return()=>clearInterval(timer);},[pending,cache,user.id,path]);
  return <section className="panel space-y-5"><h2 className="text-xl font-semibold">Evidencias privadas</h2><p className="text-sm">PDF hasta 20 MiB, imágenes hasta 10 MiB y XML hasta 5 MiB. La descarga se habilita después de verificar el archivo y completar el escaneo.</p>
    <ActionForm title="Adjuntar evidencia" submit="Cargar archivo privado" run={async(d,k)=>{
      const file=d.get("file");if(!(file instanceof File)||!file.size)throw new Error("Seleccione un archivo.");
      const mediaType=file.name.toLowerCase().endsWith(".xml")?"application/xml":file.type;
      const bytes=await file.arrayBuffer();const digest=await crypto.subtle.digest("SHA-256",bytes);const sha256=[...new Uint8Array(digest)].map(v=>v.toString(16).padStart(2,"0")).join("");
      const session=await send<{document:DocumentInfo;uploadPath:string}>("/api/v1/documents/upload-sessions","POST",{projectId,filename:file.name,mediaType,size:file.size,sha256,documentClass:text(d,"class")},k);
      const current=await apiRequest<DocumentInfo>(`/api/v1/documents/${session.document.id}`);
      if(current.state==="RESERVED") await apiRequest(session.uploadPath,{method:"PUT",headers:{"Content-Type":"application/octet-stream"},body:bytes});
      await send(`/api/v1/documents/${session.document.id}/complete-upload`,"POST",undefined,k);
    }}><label>Archivo<input name="file" type="file" accept=".pdf,.png,.jpg,.jpeg,.xml" required/></label><label>Clase documental<select name="class"><option value="EVIDENCE">Evidencia operativa</option><option value="CONTRACT">Contrato</option><option value="PHOTO">Fotografía privada</option></select></label></ActionForm>
    {data.isPending ? <p role="status">Cargando evidencias…</p> : data.isError ? <p role="alert">No se pudieron consultar las evidencias.</p> : <ul className="space-y-4">{data.data?.items.map(d=><li className="space-y-3 rounded-xl border border-border p-4" key={d.id}><p className="break-all font-semibold">{d.filename}</p><p className="text-sm">{({RESERVED:"Reservado",QUARANTINED:"En cuarentena",CLEAN:"Limpio",INFECTED:"Bloqueado por infección",REJECTED:"Rechazado",EXPIRED:"Reserva caducada"})[d.state]} · {({INTERNAL:"Interno",CLIENT:"Compartido con cliente",SUPPLIER:"Compartido con proveedor"})[d.audience]}</p><p className="break-all text-xs">Identificador de evidencia: {d.id}</p>{d.scanError && <p role="status" className="text-sm">El archivo requiere revisión: {d.scanError === "SCANNER_UNAVAILABLE" ? "escaneo temporalmente no disponible" : "verificación no completada"}.</p>}
      {d.state==="CLEAN"&&<a href={`/api/v1/documents/${d.id}/content`} className="inline-block font-semibold underline">Descargar {d.filename}</a>}
      {user.roles.includes("GERENTE")&&d.state==="CLEAN"&&<ActionForm title="Cambiar audiencia del archivo" submit="Guardar audiencia" run={(data,key)=>send(`/api/v1/documents/${d.id}/sharing`,"PUT",{audience:text(data,"audience"),recipientId:text(data,"recipient")||null,reason:text(data,"reason")},key,d.version)}><label>Audiencia<select name="audience" defaultValue={d.audience}><option value="INTERNAL">Gerencia y operación autorizada</option><option value="CLIENT">Compartir con cliente de esta obra</option><option value="SUPPLIER">Compartir con un proveedor asignado</option></select></label><Field label="Cuenta del proveedor destinatario" name="recipient" required={false}/><Field label="Motivo" name="reason" maxLength={500}/></ActionForm>}
    </li>)}</ul>}
    {data.data?.items.length===0&&<p>No hay evidencias accesibles.</p>}
  </section>;
}
