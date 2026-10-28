"use client";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { motion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AccountPicker, ConfirmAccount, roleLabel, type Account } from "@/features/accounts/account-picker";
import { InvitationAcceptance } from "@/features/accounts/invitations";
import { usePendingInvitation } from "@/features/accounts/invitation-store";
import { PendingAccount } from "@/features/accounts/onboarding";
import { apiRequest, ApiError, type Actor, type Page, type Resource, type Job } from "@/lib/api/transport";

export function Workspace() {
  const router = useRouter();
  const cache = useQueryClient();
  const session = useQuery({ queryKey: ["session"], queryFn: () => apiRequest<Actor>("/api/v1/session") });
  const user = session.data;
  const {pending,invalid}=usePendingInvitation();
  const resources = useQuery({ queryKey: ["resources", user?.id], queryFn: () => apiRequest<Page>("/api/v1/foundation/resources"), enabled: Boolean(user?.roles.length) });
  const [name, setName] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [jobId, setJobId] = useState<string>();
  const command = useRef<{ input: string; key: string } | null>(null);
  const job = useQuery({ queryKey: ["job", user?.id, jobId], queryFn: () => apiRequest<Job>(`/api/v1/jobs/${jobId}`), enabled: Boolean(user && jobId),
    refetchInterval: (query) => ["SUCCEEDED", "DEAD"].includes(query.state.data?.state ?? "") ? false : 2000 });
  async function act(action: () => Promise<void>) {
    setBusy(true); setNotice("");
    try { await action(); } catch (error) { setNotice(error instanceof Error ? error.message : "No se pudo completar la acción."); }
    finally { setBusy(false); }
  }
  if (session.isPending) return <p role="status">Consultando tu cuenta…</p>;
  if (!user) return <section className="panel max-w-lg"><h1 className="text-2xl font-semibold">Accede a tu espacio</h1>
    <p className="my-5">{session.error instanceof ApiError && session.error.status === 401 ? "Inicia sesión para continuar." : "No se pudo conectar con el servicio. Reintenta cuando esté disponible."}</p>
    <Button asChild><a href="/auth/login">Ingresar</a></Button></section>;
  const manager = user.roles.includes("GERENTE");
  if(user.roles.length===0)return <div className="space-y-4">{notice&&<p role="status">{notice}</p>}<PendingAccount user={user} onNotice={setNotice}/></div>;
  return <div className="space-y-7">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="mb-2 text-sm text-slate-600">Espacio de trabajo</p>
      <h1 className="text-3xl font-semibold">Bienvenido a ATO</h1><p className="mt-3 text-sm">{user.email}</p>
      <p className="mt-2 text-sm text-slate-600">{user.roles.map(role=>roleLabel[role]).join(" · ")}</p>
    </div><Button variant="outline" disabled={busy} onClick={() => act(async () => {
      await apiRequest<void>("/auth/logout", { method: "POST" }); cache.clear(); router.push("/");
    })}>Cerrar sesión</Button></div>
    <nav aria-label="Gestión de obras" className="flex flex-wrap gap-5 font-semibold underline underline-offset-4">{user.roles.includes("CLIENTE")&&<Link href="/cliente">Mis obras</Link>}{(manager||user.roles.includes("ASISTENTE_ADMINISTRATIVA")||user.roles.includes("PROVEEDOR"))&&<Link href="/obras">Obras</Link>}{(manager||user.roles.includes("ASISTENTE_ADMINISTRATIVA"))&&<Link href="/cartera">Clientes y proveedores</Link>}{manager&&<Link href="/usuarios">Usuarios y accesos</Link>}<Link href="/privacidad">Privacidad</Link></nav>
    <p className="rounded-lg border border-border bg-muted p-4 text-sm">Los recursos de esta página son ejemplos de desarrollo para comprobar el acceso y el seguimiento.</p>
    {notice && <p role="status" className="panel border-l-4 border-l-accent">{notice}</p>}
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <section className="panel"><h2 className="text-xl font-semibold">Mis recursos</h2>
        {resources.isPending ? <p className="mt-5" role="status">Cargando…</p> : resources.isError ? <p className="mt-5" role="alert">No se pudieron cargar tus recursos.</p> :
          <ul className="mt-5 space-y-3">{resources.data?.items.map((resource) => <motion.li key={resource.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-4">
            <div><p className="font-semibold">{resource.name}</p><p className="mt-1 text-xs text-slate-600">Revisión {resource.version}</p></div>
            {manager && <Button variant="outline" disabled={busy} onClick={() => act(async () => {
              const result = await apiRequest<{ id: string }>(`/api/v1/foundation/resources/${resource.id}/receipts`, { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() } }); setJobId(result.id);
            })}>Generar constancia</Button>}
          </motion.li>)}</ul>}
        {resources.data?.items.length === 0 && <p className="mt-5 text-slate-600">Aún no tienes recursos asignados.</p>}
        {job.data && <p role="status" className="mt-5 rounded-lg bg-muted p-4 text-sm">Constancia: {({ PENDING: "pendiente", RUNNING: "en proceso", SUCCEEDED: "completada", DEAD: "requiere revisión" })[job.data.state]}</p>}
        {manager && <form className="mt-7 grid gap-4 border-t border-border pt-6" onSubmit={(event) => { event.preventDefault(); void act(async () => {
          const input = JSON.stringify({ name: name.trim() });
          if (command.current?.input !== input) command.current = { input, key: crypto.randomUUID() };
          await apiRequest<Resource>("/api/v1/foundation/resources", { method: "POST", headers: { "Idempotency-Key": command.current.key }, body: input });
          command.current = null; setName(""); await cache.invalidateQueries({ queryKey: ["resources", user.id] }); setNotice("Recurso creado.");
        }); }}><label>Nombre del recurso<input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></label><Button disabled={busy}>Crear recurso de prueba</Button></form>}
      </section>
      {(pending||invalid)&&<section className="panel space-y-4"><h2 className="text-xl font-semibold">Mi invitación</h2><InvitationAcceptance/></section>}
    </div>
    {manager && <Management user={user} onNotice={setNotice} resources={resources.data?.items??[]} />}
    <Link href="/fundamentos/compatibilidad" className="inline-block text-sm font-semibold underline underline-offset-4">Ver cronograma de ejemplo</Link>
  </div>;
}

function Management({ user, onNotice, resources }: { user: Actor; onNotice: (message: string) => void; resources:Resource[] }) {
  const [busy,setBusy]=useState(false),[selected,setSelected]=useState<Account|null>(null);
  return <div><section className="panel"><h2 className="mb-5 text-xl font-semibold">Asignar acceso a un recurso de prueba</h2>
    <form className="grid gap-4" onSubmit={async(event)=>{event.preventDefault();const data=new FormData(event.currentTarget);setBusy(true);try{await apiRequest<void>(`/api/v1/foundation/resources/${data.get("resource")}/members`,{method:"PUT",body:JSON.stringify({userId:data.get("user"),active:data.get("active")==="true"})});onNotice("Asignación actualizada.");}catch(error){onNotice(error instanceof Error?error.message:"Acción no completada.");}finally{setBusy(false);}}}>
      <label>Recurso<select name="resource" required defaultValue=""><option value="">Selecciona un recurso</option>{resources.map(r=><option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
      <AccountPicker user={user} name="user" initialEmail={user.email} onSelect={setSelected}/><ConfirmAccount account={selected}/>
      <label>Acceso<select name="active"><option value="true">Conceder</option><option value="false">Revocar</option></select></label><Button disabled={busy} variant="outline">Guardar acceso</Button>
    </form>
  </section></div>;
}
