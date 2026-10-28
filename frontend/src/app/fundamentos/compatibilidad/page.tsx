import { CompatibilitySpike } from "@/features/scheduling/compatibility-spike";
import Link from "next/link";
export default function Page() {
  return <div className="space-y-6"><Link className="text-sm font-semibold underline underline-offset-4" href="/fundamentos">Volver al espacio de trabajo</Link>
    <h1 className="text-3xl font-semibold">Cronograma de ejemplo</h1><p className="text-slate-600">Datos ficticios para comprobar la visualización. El editor de obras se incorporará en M3.</p>
    <section className="panel"><CompatibilitySpike /></section></div>;
}
