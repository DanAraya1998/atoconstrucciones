import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { publicData, publicMetadata, type PublicProject } from "@/features/publication/public-data";
export const dynamic = "force-dynamic";
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }) { const { slug } = await params; const p = await publicData<PublicProject>(`projects/${encodeURIComponent(slug)}`); return publicMetadata(p?.title ?? "Proyecto no disponible", `/proyectos/${slug}`, p?.summary ?? "", Boolean(p)); }
export default async function Project({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params, p = await publicData<PublicProject>(`projects/${encodeURIComponent(slug)}`); if (!p) notFound();
  return <article><Link href="/proyectos" className="inline-block py-3 underline">Volver a proyectos</Link><p className="eyebrow mt-7">{p.category}{p.region && ` · ${p.region}`}{p.year && ` · ${p.year}`}</p><h1 className="mt-4 text-4xl font-semibold">{p.title}</h1><p className="my-8 max-w-3xl whitespace-pre-line text-lg leading-relaxed">{p.summary}</p><div className="grid gap-6 md:grid-cols-2">{p.photos.map((photo, i) => <Image unoptimized key={photo.url} src={photo.url} alt={photo.alt} width={photo.width} height={photo.height} priority={i === 0} sizes="(max-width: 768px) 100vw, 50vw" className="h-auto w-full rounded-xl" />)}</div><Link href="/contacto" className="mt-10 inline-block rounded-lg bg-primary px-6 py-3 font-semibold text-white">Consultar por una obra</Link></article>;
}
