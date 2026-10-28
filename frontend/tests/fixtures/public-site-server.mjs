import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { URL } from "node:url";

// Fixture de presentación, solo loopback; no importa módulos ni credenciales del backend.
let scenario = "sample";
const images = ["ato-concept-exterior.webp", "ato-concept-interior.webp", "ato-concept-detail.webp"];
const publicProjects = images.map((_, i) => ({
  slug: `publicacion-sintetica-${i + 1}`, title: `Publicación autorizada ${i + 1}`, summary: "Descripción editorial sintética aprobada para la prueba.", category: "Categoría de prueba", region: "Región sintética", year: 2026,
  photos: [{ url: `/api/v1/public/assets/fixture-${i + 1}`, alt: `Imagen sintética autorizada ${i + 1}`, width: 1536, height: 1024 }],
}));
const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", "http://127.0.0.1:9355");
  const json = (status, body) => { response.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" }); response.end(JSON.stringify(body)); };
  if (url.pathname === "/health") return json(200, { ready: true });
  if (url.pathname === "/__scenario" && request.method === "POST") {
    const next = url.searchParams.get("value");
    if (!["sample", "approved", "draft", "unavailable", "withdrawn"].includes(next)) return json(400, { error: "Unknown fixture" });
    scenario = next; return json(200, { scenario });
  }
  if (scenario === "unavailable" && url.pathname.startsWith("/api/v1/public/")) return json(503, { error: "Synthetic outage" });
  if (url.pathname === "/api/v1/public/site") return json(200, { approved: ["approved", "withdrawn"].includes(scenario), heading: scenario === "draft" ? "PRIVATE_DRAFT_TITLE" : "Titular editorial aprobado", introduction: scenario === "draft" ? "PRIVATE_DRAFT_INTRO" : "Presentación sintética aprobada de la empresa.", about: scenario === "draft" ? "PRIVATE_DRAFT_ABOUT" : "Descripción sintética aprobada de ATO.", services: scenario === "draft" ? ["PRIVATE_DRAFT_SERVICE"] : ["Servicio aprobado uno", "Servicio aprobado dos", "Servicio aprobado tres"] });
  if (url.pathname === "/api/v1/public/projects") {
    const size = Number(url.searchParams.get("size") ?? 12), page = Number(url.searchParams.get("page") ?? 0);
    return json(200, { items: scenario === "approved" ? publicProjects.slice(page * size, (page + 1) * size) : [], page, size });
  }
  if (url.pathname.startsWith("/api/v1/public/projects/")) {
    const project = publicProjects.find(p => url.pathname.endsWith("/" + p.slug));
    return scenario === "approved" && project ? json(200, project) : json(404, { error: "Not available" });
  }
  if (url.pathname.startsWith("/api/v1/public/assets/")) {
    if (scenario !== "approved") return json(404, { error: "Not available" });
    const index = Number(url.pathname.split("fixture-")[1]) - 1;
    if (!images[index]) return json(404, {});
    const bytes = await readFile(new URL(`../../public/images/${images[index]}`, import.meta.url));
    response.writeHead(200, { "Content-Type": "image/webp", "Cache-Control": "no-store" }); return response.end(bytes);
  }
  if (url.pathname === "/api/v1/public/privacy-notice") { response.writeHead(204); return response.end(); }
  return json(401, { code: "UNAUTHENTICATED" });
});
server.listen(9355, "127.0.0.1");
for (const signal of ["SIGTERM", "SIGINT"]) process.on(signal, () => server.close(() => process.exit(0)));
