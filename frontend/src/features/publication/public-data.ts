import type { Metadata } from "next";
import type { components } from "@/lib/api/schema";
export type PublicProject = components["schemas"]["PublicProject"];
export type PublicSite = components["schemas"]["PublicSiteContent"];
export type Notice = components["schemas"]["PrivacyNotice"];
const backend = process.env.ATO_BACKEND_ORIGIN ?? "http://127.0.0.1:8080";
export const publicOrigin = process.env.ATO_PUBLIC_ORIGIN ?? "https://atoconstrucciones.com";
export async function publicData<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${backend}/api/v1/public/${path}`, { cache: "no-store", signal: AbortSignal.timeout(5000) });
    if (!response.ok || response.status === 204) return null;
    return await response.json() as T;
  } catch { return null; }
}
export function publicMetadata(title: string, path: string, description: string, approved = true): Metadata {
  return { title: `${title} · ATO Construcciones`, description, alternates: { canonical: `${publicOrigin}${path}` }, robots: { index: approved, follow: approved }, openGraph: { title, description, url: `${publicOrigin}${path}`, locale: "es_CR", type: "website" } };
}
