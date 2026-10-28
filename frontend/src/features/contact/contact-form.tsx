"use client";
import { useRef, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import type { Notice } from "@/features/publication/public-data";
import type { components } from "@/lib/api/schema";
export function ContactForm({ notice }: { notice: Notice | null }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [receipt, setReceipt] = useState<string | null>(null);
  const retry = useRef<{ body: string; key: string } | null>(null), summary = useRef<HTMLParagraphElement>(null);
  if (!notice) return <p role="status" className="panel">El formulario estará disponible cuando se complete el aviso de privacidad. Por ahora no se reciben consultas desde este sitio.</p>;
  if (receipt) return <div role="status" className="panel"><h2 className="text-xl font-semibold">Solicitud recibida.</h2><p className="mt-4">Tu consulta quedó registrada. Esta confirmación no acredita entrega de correo, lectura ni respuesta.</p><p className="mt-4 break-all text-sm">Referencia: {receipt}</p></div>;
  return <form className="panel grid gap-5" onSubmit={async (event) => {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const body = JSON.stringify({ name: data.get("name"), email: data.get("email"), message: data.get("message"), noticeId: notice.id, privacyAccepted: data.get("privacyAccepted") === "on", website: data.get("website") ?? "" });
    if (retry.current?.body !== body) retry.current = { body, key: crypto.randomUUID() };
    setBusy(true); setError("");
    try {
      const response = await fetch("/api/v1/public/contact", { method: "POST", credentials: "omit", cache: "no-store", headers: { "Content-Type": "application/json", "Idempotency-Key": retry.current.key }, body });
      if (!response.ok) { const problem = await response.json() as components["schemas"]["Problem"]; throw new Error(problem.detail ?? "No se pudo guardar tu consulta."); }
      const value = await response.json() as components["schemas"]["ContactReceipt"]; setReceipt(value.reference); retry.current = null;
    } catch (e) { setError(e instanceof Error ? e.message : "No se pudo guardar tu consulta. Puedes reintentar."); requestAnimationFrame(() => summary.current?.focus()); }
    finally { setBusy(false); }
  }}>
    <label>Nombre de contacto<input name="name" autoComplete="name" required maxLength={160} /></label>
    <label>Correo para responder<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>
    <label>Cuéntanos sobre tu proyecto<textarea name="message" required minLength={10} maxLength={3000} /></label>
    <div hidden aria-hidden="true"><label>Sitio web<input name="website" tabIndex={-1} autoComplete="off" /></label></div>
    <p className="text-sm text-slate-600">El contacto se registra para atender tu consulta. No incluyas información financiera ni documentos privados.</p>
    <label className="flex items-start gap-3"><input className="mt-1 !h-5 !min-h-5 !w-5 shrink-0" name="privacyAccepted" type="checkbox" required /><span>Acepto el tratamiento descrito en el <Link className="underline" href="/aviso-privacidad" target="_blank" rel="noopener">aviso de privacidad (versión {notice.revision}, abre otra pestaña)</Link>.</span></label>
    <Button disabled={busy}>{busy ? "Registrando…" : "Enviar consulta"}</Button>
    {error && <p ref={summary} tabIndex={-1} role="alert" className="rounded-lg bg-red-50 p-4 text-red-900">{error}</p>}
  </form>;
}
