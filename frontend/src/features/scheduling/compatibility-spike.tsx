"use client";
import dynamic from "next/dynamic";

// SVAR necesita el navegador para inicializar sus stores y medir el calendario.
export const CompatibilitySpike = dynamic(() => import("./gantt-example"), {
  ssr: false,
  loading: () => <div className="h-[440px]" role="status">Cargando cronograma de ejemplo…</div>,
});

