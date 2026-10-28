import type { MetadataRoute } from "next";
import { publicOrigin } from "@/features/publication/public-data";
export default function robots(): MetadataRoute.Robots {
  return { rules: { userAgent: "*", allow: ["/", "/ato", "/proyectos", "/contacto", "/aviso-privacidad"], disallow: ["/api/", "/auth/", "/oauth2/", "/login", "/invitacion", "/fundamentos", "/cartera", "/obras", "/finanzas", "/cliente", "/privacidad", "/publicacion", "/usuarios"] }, sitemap: `${publicOrigin}/sitemap.xml` };
}
