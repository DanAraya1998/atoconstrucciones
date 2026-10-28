"use client";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type ReactNode } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ApiError, apiRequest, type Actor } from "@/lib/api/transport";
import type { components } from "@/lib/api/schema";
import { PendingAccount } from "@/features/accounts/onboarding";
export type Project = components["schemas"]["Project"];
export type Party = components["schemas"]["Party"];
export type Contract = components["schemas"]["Contract"];
export type Extra = components["schemas"]["Extra"];
export type DocumentInfo = components["schemas"]["Document"];
export function useSession() { return useQuery({ queryKey: ["session"], queryFn: () => apiRequest<Actor>("/api/v1/session") }); }
export function useData<T>(user: Actor | undefined, path: string) {
  return useQuery({ queryKey: ["m1", user?.id, path], queryFn: () => apiRequest<T>(path), enabled: Boolean(user), retry: false });
}
export function Shell({ children, title }: { children: (user: Actor) => ReactNode; title: string }) {
  const session = useSession();
  if (session.isPending) return <p role="status">Consultando tu cuenta…</p>;
  if (!session.data) return <section className="panel"><h1 className="text-2xl font-semibold">Accede a tu espacio</h1><p className="my-4">{session.error instanceof ApiError && session.error.status === 401 ? "Inicia sesión para continuar." : "El servicio no está disponible. Reintenta más tarde."}</p><Button asChild><a href="/auth/login">Ingresar</a></Button></section>;
  if (session.data.roles.length === 0) return <PendingAccount user={session.data} />;
  const operational=session.data.roles.includes("GERENTE")||session.data.roles.includes("ASISTENTE_ADMINISTRATIVA");
  return <div className="space-y-6"><nav aria-label="Espacio privado" className="flex flex-wrap gap-5 text-sm font-semibold underline underline-offset-4">{session.data.roles.includes("CLIENTE")&&<Link href="/cliente">Mis obras</Link>}{(operational||session.data.roles.includes("PROVEEDOR"))&&<Link href="/obras">Obras</Link>}{operational&&<><Link href="/cartera">Clientes y proveedores</Link><Link href="/finanzas">Finanzas</Link></>}{session.data.roles.includes("GERENTE")&&<Link href="/usuarios">Usuarios y accesos</Link>}{operational&&<Link href="/publicacion">Publicación y contactos</Link>}<Link href="/privacidad">Privacidad</Link><Link href="/fundamentos">Mi cuenta</Link></nav><h1 className="text-3xl font-semibold">{title}</h1>{children(session.data)}</div>;
}
export function ActionForm({ title, submit, children, run, normalizeData, resetConfirmationOnChange = false }: { title: string; submit: string; children: ReactNode; run: (data: FormData, key: string) => Promise<unknown>; normalizeData?: (data: FormData) => void; resetConfirmationOnChange?: boolean }) {
  const cache = useQueryClient();
  const [busy, setBusy] = useState(false), [notice, setNotice] = useState("");
  const retry = useRef<{ body: string; key: string } | null>(null);
  return <details className="min-w-0 break-words rounded-xl border border-border bg-white p-5"><summary className="cursor-pointer font-semibold">{title}</summary><form className="mt-5 grid min-w-0 gap-4" onChange={event => {if(resetConfirmationOnChange && (event.target as HTMLInputElement).name !== "confirmed"){const confirmed=event.currentTarget.elements.namedItem("confirmed");if(confirmed instanceof HTMLInputElement)confirmed.checked=false;}}} onSubmit={async (event) => {
    event.preventDefault(); const data = new FormData(event.currentTarget); normalizeData?.(data);
    const body = JSON.stringify([...data.entries()].map(([k,v]) => [k, typeof v === "string" ? v : `${v.name}:${v.size}:${v.lastModified}`]));
    if (retry.current?.body !== body) retry.current = { body, key: crypto.randomUUID() };
    setBusy(true); setNotice("");
    try { await run(data, retry.current.key); retry.current = null; await cache.invalidateQueries({ queryKey: ["m1"] }); setNotice("Cambio guardado."); }
    catch (error) {
      if (error instanceof ApiError && error.status === 412) { retry.current = null; await cache.invalidateQueries({ queryKey: ["m1"] }); setNotice("Otro usuario cambió este registro. Se actualizaron los datos; revisa la versión antes de volver a guardar."); }
      else setNotice(error instanceof Error ? error.message : "No se pudo completar la acción.");
    } finally { setBusy(false); }
  }}>{children}<Button disabled={busy}>{busy ? "Guardando…" : submit}</Button>{notice && <p role="status" className="rounded-lg bg-muted p-3 text-sm">{notice}</p>}</form></details>;
}
export function Field({ label, name, type = "text", value, required = true, maxLength, min, max, step }: { label: string; name: string; type?: string; value?: string; required?: boolean; maxLength?: number; min?: string; max?: string; step?: string }) {
  return <label>{label}<input name={name} type={type} defaultValue={value} required={required} maxLength={maxLength} min={min} max={max} step={step} /></label>;
}
export const text = (data: FormData, name: string) => String(data.get(name) ?? "").trim();
export const money = (data: FormData, name: string) => ({ amount: text(data, name), currency: "CRC" });
export function send<T>(path: string, method: string, body: unknown, key: string, version?: number) {
  return apiRequest<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body), headers: { "Idempotency-Key": key, ...(version !== undefined ? { "If-Match": `"${version}"` } : {}) } });
}
export function Reauthenticate() { return <p className="text-sm text-slate-600">Las aprobaciones y cambios de acceso requieren una contraseña reciente. <a href="/auth/login" className="font-semibold underline">Reautenticar</a></p>; }

export function civilDate(value: string) {
  return new Intl.DateTimeFormat("es-CR",{timeZone:"America/Costa_Rica",dateStyle:"medium"}).format(new Date(`${value}T12:00:00-06:00`));
}
export function crcAmount(value: string) {
  const negative=value.startsWith("-"),[whole, fraction = "00"] = value.replace(/^-/,"").split(".");
  return `${negative?"−":""}${new Intl.NumberFormat("es-CR").format(BigInt(whole))},${fraction.padEnd(2,"0")}`;
}
