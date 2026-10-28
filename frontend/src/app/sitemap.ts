import type { MetadataRoute } from "next";
import { publicData, publicOrigin, type PublicProject, type PublicSite } from "@/features/publication/public-data";
export const dynamic = "force-dynamic";
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const site = await publicData<PublicSite>("site");
  const routes = ["/proyectos", "/contacto", "/aviso-privacidad", ...(site?.approved ? ["/", "/ato"] : [])];
  const result: MetadataRoute.Sitemap = routes.map((path) => ({ url: publicOrigin + path }));
  for (let page = 0; page < 499; page++) {
    const data = await publicData<{ items: PublicProject[] }>(`projects?page=${page}&size=100`);
    for (const p of data?.items ?? []) result.push({ url: `${publicOrigin}/proyectos/${p.slug}` });
    if (!data || data.items.length < 100) break;
  }
  return result;
}
