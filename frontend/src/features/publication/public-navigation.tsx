import Link from "next/link";
import { ArrowUpRight, Menu } from "lucide-react";
const destinations = [["/", "Inicio"], ["/ato", "ATO"], ["/#servicios", "Servicios"], ["/proyectos", "Galería"], ["/contacto", "Contacto"], ["/login", "Login"]];
function Links() { return destinations.map(([href, label]) => label === "Login"
  ? <a key={href} href={href} className="public-nav-link public-nav-account">{label}<ArrowUpRight size={16} aria-hidden="true" /></a>
  : <Link key={href} href={href} className="public-nav-link">{label}</Link>); }
export function PublicNavigation() {
  return <><nav aria-label="Navegación principal" className="hidden items-center gap-1 md:flex"><Links /></nav>
    <details className="md:hidden"><summary aria-label="Menú principal" className="flex min-h-11 min-w-11 cursor-pointer list-none items-center justify-center rounded-lg border border-border p-3"><Menu size={20} /></summary><nav aria-label="Navegación principal" className="absolute left-0 top-full z-20 flex w-full flex-col gap-2 border-b border-border bg-white p-5"><Links /></nav></details>
  </>;
}
