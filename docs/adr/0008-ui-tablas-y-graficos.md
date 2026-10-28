# 0008 — Sistema visual, tablas y gráficos

**Estado:** Propuesto para selecciones adicionales; stack frontend confirmado. **Fecha:** 7 de octubre de 2026. **HU:** HU-00/HU-07/HU-08. **BK:** BK-002/BK-024/BK-025/BK-028. **Pendiente:** P-13. [UX](../07-diseno-ux-ui.md) · [Evaluación/licencias](../04-arquitectura-tecnica.md).

## Contexto y propuesta

Tres familias de experiencia (público, administración y portales externos) comparten identidad pero tienen densidad/movimiento diferentes; cliente/proveedor tienen navegación propia. Tokens del logo muestreados provisionales; shadcn/Tailwind, tablas HTML simples y TanStack Table cuando el volumen lo justifique; Recharts para indicadores iniciales, Motion con reduced motion; GSAP opcional en landing.

## Alternativas y consecuencias

Grid comercial puede resolver edición masiva, pero no hay necesidad actual; ECharts se reserva para series densas medidas. Un Gantt o tabla reducido de ancho no soluciona móvil: agenda/tarjetas/editor son patrones propios. La paleta del JPG no es manual oficial; naranja/blanco no cumple contraste de texto normal.

## Verificación

BK-002 confirma peers/SSR/licencias y bundle final; T-UI/T-A11Y/T-PERF prueban tareas con teclado/móvil y cifras legibles. P-13 confirma texto/fotos/marca antes de publicación. Reconsiderar biblioteca solo con caso/medición y ADR.
