import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { PublicNavigation } from "@/features/publication/public-navigation";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = { title: "ATO Construcciones", description: "ATO Construcciones", robots: { index: false, follow: false } };
export default function Layout({ children }: { children: React.ReactNode }) {
  return <html lang="es-CR"><body><Providers>
    <a href="#contenido" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-white focus:p-4">Saltar al contenido</a>
    <header className="site-header"><div className="site-header-inner">
      <Link href="/" className="shrink-0" aria-label="ATO Construcciones, inicio"><Image className="h-auto w-28 md:w-32" src="/ato-logo.jpg" alt="ATO Construcciones" width={160} height={85} priority /></Link>
      <PublicNavigation />
    </div></header>
    <main id="contenido" className="site-main mx-auto max-w-6xl px-6 py-10">{children}</main>
    <footer className="site-footer"><div className="footer-inner"><div className="footer-brand"><Link href="/" aria-label="ATO Construcciones, volver al inicio">ATO<span>CONSTRUCCIONES</span></Link><span className="footer-brand-rule" /></div><nav aria-label="Enlaces del pie de página"><p>Explorar</p><Link href="/ato">Nuestra empresa</Link><Link href="/proyectos">Proyectos y galería</Link><Link href="/#servicios">Servicios</Link></nav><div className="footer-contact"><p>Conversemos</p><Link href="/contacto">Contactar a ATO <ArrowUpRight size={20} aria-hidden="true" /></Link><a href="/login">Acceso a mi cuenta</a></div><div className="footer-bottom"><span>© {new Date().getFullYear()} ATO Construcciones</span><Link href="/aviso-privacidad">Aviso de privacidad</Link></div></div></footer>
  </Providers></body></html>;
}
