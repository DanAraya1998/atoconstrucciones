import Link from "next/link";
import { EditorialGallery } from "@/features/publication/editorial-gallery";
import { publicData, publicMetadata, type PublicProject } from "@/features/publication/public-data";
export const dynamic = "force-dynamic";
export const metadata = publicMetadata("Proyectos", "/proyectos", "Galería de proyectos autorizados de ATO Construcciones.");
export default async function Projects({ searchParams }: { searchParams: Promise<{ pagina?: string }> }) {
  const raw = (await searchParams).pagina ?? "0", page = /^\d{1,5}$/.test(raw) ? Number(raw) : 0;
  const gallery = await publicData<{ items: PublicProject[] }>(`projects?page=${page}&size=12`);
  return <section className="public-page public-gallery"><div className="section-heading-row"><div><p className="section-kicker"><span className="orange-rule" /> Proyectos y portafolio</p><h1 className="display-heading">Una mirada<br /><em>a la galería.</em></h1></div><Link href="/contacto" className="editorial-action">Hablemos de tu proyecto <span aria-hidden="true">↗</span></Link></div>{!gallery?.items.length && <p className="gallery-sample-note">{gallery ? "No hay proyectos autorizados en esta página." : "No se pudo consultar la galería. Intenta nuevamente más tarde."}{page === 0 && " Las siguientes son imágenes conceptuales de muestra; no representan obras realizadas por ATO."}</p>}{gallery?.items.length || page === 0 ? <EditorialGallery projects={gallery?.items ?? []} /> : null}<nav aria-label="Páginas de proyectos" className="mt-8 flex gap-6">{page > 0 && <Link href={`/proyectos?pagina=${page - 1}`} className="py-3 underline">Página anterior</Link>}{gallery?.items.length === 12 && <Link href={`/proyectos?pagina=${page + 1}`} className="py-3 underline">Página siguiente</Link>}</nav></section>;
}
