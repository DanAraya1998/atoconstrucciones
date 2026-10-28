import Link from "next/link";
import { publicData, publicMetadata, type PublicSite } from "@/features/publication/public-data";
import { ConceptPhoto } from "@/features/publication/concept-photo";
import { landingContent } from "@/features/publication/landing-content";
import { ArrowUpRight } from "lucide-react";
export const dynamic = "force-dynamic";
export async function generateMetadata() { const site = await publicData<PublicSite>("site"); return publicMetadata("ATO", "/ato", "Conoce ATO Construcciones.", site?.approved ?? false); }
export default async function About() {
  const site = await publicData<PublicSite>("site"), copy = landingContent(site);
  return <section className="public-page public-about"><div className="public-page-intro"><p className="section-kicker"><span className="orange-rule" /> Nuestra empresa</p>{copy.sample && <p className="sample-label sample-label-light">Contenido de muestra · Lorem ipsum</p>}<h1 className="display-heading">{copy.sample ? <>Lorem ipsum,<br /><em>consectetur.</em></> : <>ATO<br /><em>Construcciones.</em></>}</h1><p className="section-copy whitespace-pre-line">{copy.about}</p><Link href="/contacto" className="public-button public-button-blue">Contactar a ATO <ArrowUpRight size={19} aria-hidden="true" /></Link></div><ConceptPhoto image="detail" /></section>;
}
