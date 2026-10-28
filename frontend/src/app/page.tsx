import { ArrowDown, ArrowRight, ArrowUpRight, DraftingCompass, Layers3, MoveUpRight, Ruler } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { ConceptPhoto } from "@/features/publication/concept-photo";
import { EditorialGallery } from "@/features/publication/editorial-gallery";
import { landingContent, lorem } from "@/features/publication/landing-content";
import { publicData, publicMetadata, type PublicProject, type PublicSite } from "@/features/publication/public-data";
export const dynamic = "force-dynamic";
export async function generateMetadata() {
  const site = await publicData<PublicSite>("site");
  return publicMetadata("Inicio", "/", site?.approved ? site.introduction : "ATO Construcciones. Diseño de muestra con textos lorem ipsum.", site?.approved ?? false);
}
export default async function Home() {
  const [site, gallery] = await Promise.all([publicData<PublicSite>("site"), publicData<{ items: PublicProject[] }>("projects?size=3")]);
  const copy = landingContent(site), photo = gallery?.items[0]?.photos[0];
  const icons = [DraftingCompass, Ruler, Layers3];
  return <div className="landing">
    <section className="landing-hero" aria-labelledby="hero-heading">
      {photo ? <Image unoptimized src={photo.url} alt={photo.alt} fill priority fetchPriority="high" sizes="(max-width: 1280px) 100vw, 1280px" className="object-cover" /> : <ConceptPhoto className="hero-concept" priority sizes="(max-width: 1280px) 100vw, 1280px" />}
      <div className="hero-shade" />
      <div className="hero-copy">
        <p className="hero-eyebrow"><span /> ATO Construcciones</p>
        {copy.sample && <p className="sample-label">Contenido de muestra · Lorem ipsum</p>}
        <h1 id="hero-heading">{copy.sample ? <>Lorem ipsum<br /><em>dolor sit amet.</em></> : copy.heading}</h1>
        <p className="hero-introduction">{copy.introduction}</p>
        <div className="hero-actions"><Link className="public-button public-button-orange" href="/contacto">Hablemos de tu proyecto <ArrowUpRight size={19} aria-hidden="true" /></Link><a className="hero-secondary" href="#galeria">Explorar galería <ArrowDown size={17} aria-hidden="true" /></a></div>
      </div>
      <a className="hero-scroll" href="#nosotros"><span>Descubrir ATO</span><ArrowDown size={18} aria-hidden="true" /></a>
    </section>
    <div className="landing-index" aria-label="Explora ATO"><a href="#nosotros"><span>01</span> ATO <ArrowUpRight aria-hidden="true" size={17} /></a><a href="#servicios"><span>02</span> Servicios <ArrowUpRight aria-hidden="true" size={17} /></a><a href="#galeria"><span>03</span> Galería <ArrowUpRight aria-hidden="true" size={17} /></a></div>
    <section id="nosotros" className="landing-about landing-section" aria-labelledby="about-heading">
      <div className="about-visual"><ConceptPhoto image="interior" /><div className="about-stamp" aria-hidden="true"><span>ATO</span><ArrowUpRight size={30} /><small>CONSTRUCCIONES</small></div></div>
      <div className="about-copy"><p className="section-kicker"><span className="orange-rule" /> 01 / Nuestra empresa</p><h2 id="about-heading" className="display-heading">{copy.sample ? <>Lorem ipsum,<br /><em>consectetur.</em></> : <>Conoce<br /><em>ATO Construcciones.</em></>}</h2><p className="section-copy">{copy.about}</p><Link href="/ato" className="editorial-action">Conocer ATO <span><ArrowUpRight size={20} aria-hidden="true" /></span></Link></div>
    </section>
    <section id="servicios" className="landing-services landing-section" aria-labelledby="services-heading">
      <div className="section-heading-row"><div><p className="section-kicker"><span className="orange-rule" /> 02 / Servicios</p><h2 id="services-heading" className="display-heading">{copy.sample ? <>Consectetur<br /><em>adipiscing elit.</em></> : <>Servicios<br /><em>de ATO.</em></>}</h2></div><p className="section-copy section-heading-note">{copy.sample ? lorem.introduction : copy.introduction}</p></div>
      <div className="service-grid">{copy.services.map((service, i) => {const Icon = icons[i % icons.length];return <article className="service-item" key={`${service}-${i}`}><div className="service-top"><span>{String(i + 1).padStart(2, "0")}</span><Icon size={33} strokeWidth={1.25} aria-hidden="true" /></div><h3>{service}</h3>{copy.sample && <p>{lorem.paragraph}</p>}<Link href="/contacto">Consultar servicio <ArrowUpRight size={19} aria-hidden="true" /></Link></article>;})}</div>
    </section>
    <section id="galeria" className="landing-gallery landing-section" aria-labelledby="gallery-heading"><div className="section-heading-row"><div><p className="section-kicker"><span className="orange-rule" /> 03 / Galería</p><h2 id="gallery-heading" className="display-heading">{gallery?.items.length ? <>Una mirada<br /><em>a los proyectos.</em></> : <>Lorem ipsum.<br /><em>Dolor sit amet.</em></>}</h2></div><Link className="editorial-action" href="/proyectos">Ver toda la galería <span><ArrowUpRight size={20} aria-hidden="true" /></span></Link></div>
      {!gallery?.items.length && <p className="gallery-sample-note">Galería conceptual de muestra. Estas imágenes ilustrativas no representan obras realizadas por ATO.</p>}
      <EditorialGallery projects={gallery?.items ?? []} />
    </section>
    {copy.sample && <section className="landing-process landing-section" aria-labelledby="process-heading"><div className="process-heading"><p className="section-kicker">04 / El proceso · texto de muestra</p><h2 id="process-heading" className="display-heading">Lorem ipsum,<br /><em>paso a paso.</em></h2><p>{lorem.introduction}</p></div><ol className="process-steps">{["Lorem ipsum", "Dolor sit amet", "Consectetur elit"].map((step, i) => <li key={step}><span className="process-number">0{i + 1}</span><div><h3>{step}</h3><p>{lorem.paragraph}</p></div><MoveUpRight size={22} aria-hidden="true" /></li>)}</ol></section>}
    <section className="landing-contact landing-section" aria-labelledby="contact-heading"><div><p className="section-kicker"><span className="orange-rule" /> Hablemos</p><h2 id="contact-heading" className="display-heading">{copy.sample ? <>Lorem ipsum.<br /><em>Dolor sit amet?</em></> : <>Conversemos sobre<br /><em>tu proyecto.</em></>}</h2></div><div className="contact-invitation"><p className="section-copy">{copy.sample ? lorem.introduction : "Puedes enviarnos una consulta desde el formulario de contacto."}</p><Link className="public-button public-button-blue" href="/contacto">Contactar a ATO <ArrowRight size={20} aria-hidden="true" /></Link></div></section>
  </div>;
}
