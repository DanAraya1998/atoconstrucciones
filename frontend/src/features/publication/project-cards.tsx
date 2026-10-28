import Image from "next/image";
import Link from "next/link";
import type { PublicProject } from "./public-data";
export function ProjectCards({ projects }: { projects: PublicProject[] }) {
  return <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">{projects.map((p) => <article key={p.slug} className="overflow-hidden rounded-xl border border-border bg-white">
    <Link href={`/proyectos/${p.slug}`} className="block">{p.photos[0] && <Image unoptimized src={p.photos[0].url} alt={p.photos[0].alt} width={p.photos[0].width} height={p.photos[0].height} sizes="(max-width: 768px) 100vw, 33vw" className="aspect-[4/3] w-full object-cover" />}
      <div className="p-6"><p className="text-sm text-slate-600">{p.category}{p.region && ` · ${p.region}`}</p><h3 className="mt-2 text-xl font-semibold">{p.title}</h3><p className="mt-4 text-sm font-semibold underline underline-offset-4">Conocer el proyecto</p></div>
    </Link></article>)}</div>;
}
