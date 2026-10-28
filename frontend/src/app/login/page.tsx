import { redirect } from "next/navigation";
export const metadata = { title: "Ingresar · ATO Construcciones", robots: { index: false, follow: false } };
export default function Login() { redirect("/auth/login"); }
