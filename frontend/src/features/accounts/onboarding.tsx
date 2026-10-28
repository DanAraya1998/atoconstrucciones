"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { apiRequest, type Actor } from "@/lib/api/transport";
import type { components } from "@/lib/api/schema";
import { ActionForm, Field, Reauthenticate, Shell, send, text, useData, type Party, type Project } from "@/features/projects/shared";
import { roleLabel } from "./account-picker";
import { InvitationAcceptance } from "./invitations";
import { usePendingInvitation } from "./invitation-store";

type ManagedAccount = components["schemas"]["ManagedAccount"];
type Access = components["schemas"]["AccountAccessInput"];
const states: Record<string, string> = { PENDING: "Pendiente de aprobación", APPROVED: "Aprobada", REJECTED: "Solicitud rechazada", SUSPENDED: "Suspendida" };
const roles = ["CLIENTE", "PROVEEDOR", "ASISTENTE_ADMINISTRATIVA"] as const;

export function PendingAccount({ user, onNotice }: { user: Actor; onNotice?:(message:string)=>void }) {
  const cache = useQueryClient(), { pending, invalid } = usePendingInvitation();
  const account = useData<ManagedAccount>(user, "/api/v1/accounts/me");
  const [busy, setBusy] = useState(false);
  return <section className="panel mx-auto max-w-xl space-y-5">
    <h1 className="text-2xl font-semibold">{account.data?.state === "REJECTED" ? "Tu solicitud no fue aprobada" : "Tu acceso está pendiente de aprobación"}</h1>
    <p className="break-all">{user.email}</p>
    <p>{account.data?.state === "REJECTED" ? "Comunícate con gerencia si necesitas revisar tu solicitud." : "Gerencia revisará tu cuenta y te asignará los accesos correspondientes."}</p>
    {account.isError && <p role="alert">No se pudo consultar el estado de tu solicitud. Puedes reintentar.</p>}
    <div className="flex flex-wrap gap-3"><Button disabled={busy} onClick={async () => { setBusy(true); try { await account.refetch(); await cache.invalidateQueries({ queryKey: ["session"] }); } finally { setBusy(false); } }}>Consultar estado</Button>
      <Button variant="outline" onClick={async () => { await apiRequest<void>("/auth/logout", { method: "POST" }); cache.clear(); window.location.assign("/login"); }}>Cerrar sesión</Button></div>
    {(pending || invalid) && <InvitationAcceptance onAccepted={onNotice} />}
  </section>;
}

function Pagination({ page, more, change }: { page: number; more: boolean; change: (page: number) => void }) {
  return <div className="flex flex-wrap items-center gap-3"><Button type="button" variant="outline" disabled={page === 0} onClick={() => change(page - 1)}>Anterior</Button><span>Página {page + 1}</span><Button type="button" variant="outline" disabled={!more} onClick={() => change(page + 1)}>Siguiente</Button></div>;
}

function AccessFields({ user, initialParty, initialRole }: { user: Actor; initialParty?: Party; initialRole?: string }) {
  const [role, setRole] = useState(initialRole ?? "CLIENTE"), [chosenParty, setParty] = useState<Party | undefined>(initialParty);
  const [partyPage, setPartyPage] = useState(0), [projectPage, setProjectPage] = useState(0);
  const [selected, setSelected] = useState<Record<string, { projectId: string; version: number; representative: boolean; name: string }>>({});
  const operational = role === "ASISTENTE_ADMINISTRATIVA", kind = role === "CLIENTE" ? "clients" : "suppliers";
  const parties = useData<{ items: Party[] }>(operational ? undefined : user, `/api/v1/${kind}?page=${partyPage}&size=20`);
  const projects = useData<{ items: Project[] }>(user, `/api/v1/projects?page=${projectPage}&size=20`);
  const party = parties.data?.items.find(p => p.id === chosenParty?.id) ?? chosenParty;
  const available = projects.data?.items.filter(p => !["CERRADO", "CANCELADO", "EN_CIERRE"].includes(p.state) && (role !== "CLIENTE" || p.clientId === party?.id)) ?? [];
  return <>
    <label>Perfil de acceso<select name="role" value={role} onChange={e => { setRole(e.target.value); setParty(undefined); setSelected({}); setPartyPage(0); setProjectPage(0); }}>{roles.map(r => <option key={r} value={r}>{roleLabel[r]}</option>)}</select></label>
    {!operational && <fieldset className="min-w-0 space-y-3"><legend className="font-semibold">{role === "CLIENTE" ? "Cliente vinculado" : "Proveedor vinculado"}</legend>
      {parties.isError && <p role="alert">No se pudo cargar la cartera. <Button type="button" variant="outline" onClick={() => parties.refetch()}>Reintentar cartera</Button></p>}
      <label>{role === "CLIENTE" ? "Selecciona el cliente" : "Selecciona el proveedor"}<select name="partyId" required value={party?.id ?? ""} onChange={e => { setParty(parties.data?.items.find(p => p.id === e.target.value)); setSelected({}); setProjectPage(0); }}>
        <option value="">Selecciona un registro</option>{party && !parties.data?.items.some(p => p.id === party.id) && <option value={party.id}>{party.name}</option>}{parties.data?.items.filter(p => p.active).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select></label><input type="hidden" name="partyVersion" value={party?.version ?? ""} />
      <Pagination page={partyPage} more={(parties.data?.items.length ?? 0) === 20} change={setPartyPage} />
    </fieldset>}
    <fieldset className="min-w-0 space-y-3"><legend className="font-semibold">Obras autorizadas</legend><p className="text-sm">Marca las obras a las que podrá acceder. Puedes habilitar la cuenta sin asignar obras todavía.</p>
      {projects.isPending && <p role="status">Consultando obras…</p>}{projects.isError && <p role="alert">No se pudieron cargar las obras. <Button type="button" variant="outline" onClick={() => projects.refetch()}>Reintentar obras</Button></p>}
      {available.map(project => <div key={project.id} className="rounded-lg border border-border p-3 space-y-3">
        <label className="flex items-start gap-3"><input type="checkbox" className="!w-5" checked={Boolean(selected[project.id])} onChange={e => setSelected(current => { const next = { ...current }; if (e.target.checked) next[project.id] = { projectId: project.id, version: project.version, representative: false, name: project.name }; else delete next[project.id]; return next; })} /><span>{project.name} · {project.code}</span></label>
        {role === "CLIENTE" && selected[project.id] && <label className="flex items-start gap-3"><input className="!w-5" type="checkbox" checked={selected[project.id].representative} onChange={e => setSelected(current => ({ ...current, [project.id]: { ...current[project.id], representative: e.target.checked } }))} /><span>Puede representar al cliente en acuerdos de esta obra.</span></label>}
      </div>)}
      {!projects.isPending && !projects.isError && available.length === 0 && <p>No hay obras disponibles en esta página para la selección actual.</p>}
      <Pagination page={projectPage} more={(projects.data?.items.length ?? 0) === 20} change={setProjectPage} />
      {Object.keys(selected).length > 0 && <p className="text-sm">Obras seleccionadas: {Object.values(selected).map(p => p.name).join(", ")}.</p>}
      <input type="hidden" name="projects" value={JSON.stringify(Object.values(selected).map(({ projectId, version, representative }) => ({ projectId, version, representative })))} />
    </fieldset>
  </>;
}

function accessInput(data: FormData): Access {
  const role = text(data, "role") as Access["role"];
  return { role, partyId: role === "ASISTENTE_ADMINISTRATIVA" ? null : text(data, "partyId"), partyVersion: role === "ASISTENTE_ADMINISTRATIVA" ? null : Number(text(data, "partyVersion")), projects: JSON.parse(text(data, "projects")) as Access["projects"] };
}

export function DirectAccountForm({ user, party, role }: { user: Actor; party?: Party; role?: string }) {
  const capabilities = useData<{ directAvailable: boolean }>(user, "/api/v1/accounts/capabilities");
  if (capabilities.isPending) return <p role="status">Consultando disponibilidad del alta…</p>;
  if (!capabilities.data?.directAvailable) return <p>El alta directa no está disponible en este momento. {capabilities.isError && <Button type="button" variant="outline" onClick={() => capabilities.refetch()}>Reintentar</Button>}</p>;
  return <ActionForm title={party ? "Habilitar acceso" : "Crear una cuenta"} submit="Crear y habilitar" resetConfirmationOnChange run={(data, key) => send("/api/v1/accounts", "POST", { displayName: text(data, "displayName"), email: text(data, "email"), access: accessInput(data) }, key)}>
    <Field label="Nombre de la persona" name="displayName" maxLength={200} /><Field label="Correo de la persona" name="email" type="email" value={party?.email} maxLength={254} />
    <AccessFields user={user} initialParty={party} initialRole={role} />
    <p className="text-sm">La cuenta quedará aprobada con los accesos seleccionados. Se preparará el correo para verificar la dirección y configurar la contraseña; podrás consultar el estado del envío en Usuarios y accesos.</p>
    <label className="flex items-start gap-3"><input name="confirmed" type="checkbox" required className="!w-5" /><span>Confirmo la identidad y los accesos que estoy concediendo.</span></label>
  </ActionForm>;
}

export function AccountsWorkspace() { return <Shell title="Usuarios y accesos">{user => user.roles.includes("GERENTE") ? <Accounts user={user} /> : <p>La gestión de usuarios corresponde a gerencia.</p>}</Shell>; }
function Accounts({ user }: { user: Actor }) {
  const [query, setQuery] = useState(""), [draft, setDraft] = useState(""), [state, setState] = useState("PENDING"), [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ["m1", user.id, "managed-accounts", query, state, page], queryFn: () => apiRequest<components["schemas"]["ManagedAccountPage"]>("/api/v1/accounts/management-search", { method: "POST", body: JSON.stringify({ query, state, page, size: 20 }) }), retry: false, refetchInterval: 15000 });
  return <div className="space-y-6"><Reauthenticate /><DirectAccountForm user={user} />
    <form className="grid gap-4 sm:grid-cols-[1fr_auto]" onSubmit={e => { e.preventDefault(); setQuery(draft.trim()); setPage(0); }}><label>Buscar por nombre o correo<input maxLength={254} value={draft} onChange={e => setDraft(e.target.value)} /></label><Button className="self-end">Buscar usuarios</Button></form>
    <label>Estado de las cuentas<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="ALL">Todas</option>{Object.entries(states).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    {list.isPending && <p role="status">Consultando usuarios…</p>}{list.isError && <p role="alert">No se pudieron cargar los usuarios. <Button variant="outline" onClick={() => list.refetch()}>Reintentar usuarios</Button></p>}
    {list.data?.items.length === 0 && <p>No hay cuentas con estos filtros.</p>}
    <div className="space-y-4">{list.data?.items.map(account => <AccountCard key={account.id} user={user} account={account} />)}</div>
    <Pagination page={page} more={list.data?.hasMore ?? false} change={setPage} />
  </div>;
}
function AccountCard({ user, account }: { user: Actor; account: ManagedAccount }) {
  const protectedAccount = account.roles.includes("GERENTE"), path = `/api/v1/accounts/${account.id}`;
  const activation: Record<string, string> = { PENDING: "Correo de activación pendiente de envío", SENDING: "Enviando correo de activación", SENT: "Correo de activación enviado", FAILED: "No se pudo enviar el correo de activación" };
  return <article className="panel min-w-0 space-y-4"><h2 className="break-words text-xl font-semibold">{account.displayName || account.email}</h2><p className="break-all">{account.email}</p><p>{states[account.state]}{account.roles.length > 0 ? ` · ${account.roles.map(r => roleLabel[r]).join(" · ")}` : ""}</p>
    {!account.emailVerified && <p>La persona aún debe verificar su correo.{activation[account.activationState] ? ` ${activation[account.activationState]}.` : ""}</p>}
    {!protectedAccount && account.state !== "SUSPENDED" && account.emailVerified && <ActionForm key={account.version} resetConfirmationOnChange title={account.state === "APPROVED" ? "Ampliar accesos" : "Revisar y aprobar"} submit={account.state === "APPROVED" ? "Conceder accesos" : "Aprobar y conceder acceso"} run={(data, key) => send(`${path}/approve`, "POST", accessInput(data), key, account.version)}>
      <AccessFields user={user} /><input type="hidden" name="accountVersion" value={account.version} /><p>Se agregarán los accesos seleccionados a esta cuenta.</p><label className="flex items-start gap-3"><input name="confirmed" type="checkbox" required className="!w-5" /><span>Confirmo la identidad de {account.displayName || account.email} y los accesos elegidos.</span></label>
    </ActionForm>}
    {!protectedAccount && account.state === "PENDING" && <DecisionForm account={account} action="reject" title="Rechazar solicitud" submit="Confirmar rechazo" />}
    {!protectedAccount && account.state !== "SUSPENDED" && <DecisionForm account={account} action="suspend" title="Suspender cuenta" submit="Confirmar suspensión" />}
    {!protectedAccount && account.state === "SUSPENDED" && <DecisionForm account={account} action="resume" title="Reactivar cuenta" submit="Confirmar reactivación" />}
    {!protectedAccount && account.state === "APPROVED" && !account.emailVerified && ["SENT", "FAILED"].includes(account.activationState) && <ActionForm title="Reenviar activación" submit="Confirmar reenvío" run={(_, key) => send(`${path}/activation`, "POST", undefined, key, account.version)}><input type="hidden" name="accountVersion" value={account.version} /><p>Se solicitará otro correo para verificar la dirección y configurar la contraseña.</p></ActionForm>}
    {account.state === "APPROVED" && <p className="text-sm">Puedes revisar y retirar accesos concretos desde <Link href="/cartera" className="underline">Clientes y proveedores</Link> o <Link href="/obras" className="underline">Obras</Link>.</p>}
  </article>;
}
function DecisionForm({ account, action, title, submit }: { account: ManagedAccount; action: string; title: string; submit: string }) {
  return <ActionForm key={account.version} title={title} submit={submit} run={(data, key) => send(`/api/v1/accounts/${account.id}/${action}`, "POST", { reason: text(data, "reason") }, key, account.version)}><input type="hidden" name="accountVersion" value={account.version} /><Field label="Motivo" name="reason" maxLength={500} /><p>{action === "resume" ? "La cuenta recuperará el estado y los accesos que tenía antes de la suspensión." : "Se conservarán la cuenta y su historial."}</p></ActionForm>;
}
