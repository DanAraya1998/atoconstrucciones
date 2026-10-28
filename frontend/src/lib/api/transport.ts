import type { components } from "./schema";

export type Actor = components["schemas"]["Actor"];
export type Resource = components["schemas"]["Resource"];
export type Page = components["schemas"]["ResourcePage"];
export type Job = components["schemas"]["Job"];
export type Money = components["schemas"]["Money"];
export type Problem = components["schemas"]["Problem"];
export class ApiError extends Error {
  public status: number;
  public problem: Problem;
  constructor(status: number, problem: Problem) {
    super(problem.detail ?? "No se pudo completar la solicitud.");
    this.status = status;
    this.problem = problem;
  }
}
export function validateMoney(value: Money): Money {
  if (typeof value.amount !== "string" || !/^-?(0|[1-9]\d{0,17})(\.\d{1,5})?$/.test(value.amount) || !/^[A-Z]{3}$/.test(value.currency)) {
    throw new Error("Importe o moneda inválidos");
  }
  return value; // Se conserva decimal exacto; nunca Number/parseFloat.
}
export async function apiRequest<T>(path: string, options: RequestInit = {}, fetcher: typeof fetch = fetch): Promise<T> {
  if (!path.startsWith("/api/v1/") && path !== "/auth/logout") throw new Error("Ruta API no admitida");
  const headers = new Headers(options.headers);
  const method = (options.method ?? "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const csrfResponse = await fetcher("/api/v1/session/csrf", { credentials: "same-origin", cache: "no-store" });
    if (!csrfResponse.ok) throw new Error("No se pudo proteger la solicitud.");
    const csrf = await csrfResponse.json() as { headerName: string; token: string };
    headers.set(csrf.headerName, csrf.token);
  }
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetcher(path, { ...options, headers, credentials: "same-origin", cache: "no-store" });
  if (!response.ok) {
    const fallback: Problem = { type: "urn:ato:problem:service-unavailable", title: "Servicio no disponible", status: response.status, detail: "No se pudo completar la solicitud.", code: "SERVICE_UNAVAILABLE", traceId: "" };
    let problem = fallback;
    if (response.headers.get("content-type")?.includes("application/problem+json")) problem = await response.json() as Problem;
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
