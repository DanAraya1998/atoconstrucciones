import type { NextConfig } from "next";

const backend = process.env.ATO_BACKEND_ORIGIN ?? "http://127.0.0.1:8080";
const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  images: { localPatterns: ["/ato-logo.jpg", "/images/ato-concept-exterior.webp", "/images/ato-concept-interior.webp", "/images/ato-concept-detail.webp"].map(pathname => ({ pathname, search: "" })) },
  async rewrites() {
    return ["/api/v1/:path*", "/auth/:path*", "/oauth2/:path*", "/login/oauth2/:path*"].map((source) => ({
      source, destination: `${backend}${source}`,
    }));
  },
  async headers() {
    return [{ source: "/:path*", headers: [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "Referrer-Policy", value: "same-origin" },
      { key: "X-Frame-Options", value: "DENY" },
    ] }];
  },
};
export default config;
