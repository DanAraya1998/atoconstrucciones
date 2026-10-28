import { ContactForm } from "@/features/contact/contact-form";
import { publicData, publicMetadata, type Notice } from "@/features/publication/public-data";
import { lorem } from "@/features/publication/landing-content";
import { ArrowUpRight } from "lucide-react";
export const dynamic = "force-dynamic";
export const metadata = publicMetadata("Contacto", "/contacto", "Envía una consulta a ATO Construcciones.");
export default async function Contact() {
  const notice = await publicData<Notice>("privacy-notice");
  return <section className="public-page public-contact"><div className="public-page-intro"><p className="section-kicker"><span className="orange-rule" /> Contacto</p><h1 className="display-heading">Hablemos de<br /><em>tu proyecto.</em></h1><p className="section-copy">Comparte tu idea y un correo donde podamos responderte.</p><div className="contact-lorem"><ArrowUpRight size={40} strokeWidth={1} aria-hidden="true" /><p className="section-kicker">Texto de muestra</p><p>{lorem.introduction}</p></div></div><div className="contact-form-panel"><ContactForm notice={notice} /></div></section>;
}
