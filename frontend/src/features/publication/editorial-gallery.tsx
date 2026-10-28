import Image from "next/image";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { ConceptPhoto } from "./concept-photo";
import type { PublicProject } from "./public-data";

export function EditorialGallery({ projects }: { projects: PublicProject[] }) {
  if (!projects.length) return <div className="editorial-gallery" aria-label="Galería conceptual de muestra">
    {([['exterior', 'Lorem ipsum dolor'], ['interior', 'Consectetur adipiscing'], ['detail', 'Aenean commodo']] as const).map(([image, title], i) =>
      <article className="editorial-piece" key={image}>
        <ConceptPhoto image={image} sizes={i === 0 ? "(max-width: 767px) 100vw, 60vw" : "(max-width: 767px) 100vw, 40vw"} />
        <div className="editorial-caption"><div><p className="section-kicker">Concepto {String(i + 1).padStart(2, '0')}</p><h3>{title}</h3></div><span className="editorial-mark" aria-hidden="true">↗</span></div>
      </article>)}
  </div>;
  return <div className="editorial-gallery" aria-label="Publicaciones autorizadas">
    {projects.map((project, i) => <article key={project.slug} className="editorial-piece">
      <Link className="editorial-link" href={`/proyectos/${project.slug}`}>
        {project.photos[0] && <div className="editorial-image"><Image unoptimized src={project.photos[0].url} alt={project.photos[0].alt} fill sizes={i === 0 ? "(max-width: 767px) 100vw, 60vw" : "(max-width: 767px) 100vw, 40vw"} className="object-cover" /></div>}
        <div className="editorial-caption"><div><p className="section-kicker">{project.category}{project.region && ` / ${project.region}`}</p><h3>{project.title}</h3></div><span className="editorial-mark"><ArrowUpRight size={22} aria-hidden="true" /><span className="sr-only">Ver proyecto</span></span></div>
      </Link>
    </article>)}
  </div>;
}
