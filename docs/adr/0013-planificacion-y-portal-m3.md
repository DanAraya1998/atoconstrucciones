# 0013 — Planificación, avance y portal cliente M3

**Estado:** Implementado para desarrollo bajo la autorización de M3 del 8 de octubre de 2026. **HU:** HU-08, HU-01, HU-02, HU-05, HU-12. **BK:** BK-022 a BK-024. [Entrega](../20-entrega-m3.md) · [Reglas canónicas](../09-gantt-y-planificacion.md).

## Decisión y alcance

Se aplican los predeterminados conservadores de P-12: fechas civiles y días calendario, FS con lag no negativo, pesos físicos explícitos que suman exactamente 100%, aprobación/verificación exclusiva de GERENTE con contraseña reciente. Esta decisión de implementación permite desarrollar M3; no acredita que el negocio haya validado calendarios laborales o una metodología de medición distinta. Cantidades físicas automáticas, esfuerzo alternativo, feriados y funciones PRO requieren reglas y pruebas posteriores.

Spring valida el árbol y DAG completos dentro del guard Project→Schedule. Un lote incluye todas las tareas y dependencias y exige revisión global e If-Match coincidentes; 412 conserva el borrador en pantalla y exige revisión humana. Las tareas se retiran mediante estado CANCELLED, sin borrar identidad ni evidencia. Tipo y vínculo de extra se conservan; cambiar ese entregable requiere una tarea nueva.

V7 agrega tablas normalizadas y snapshots JSON inmutables por revisión. Reporte y verificación son hechos distintos; una corrección necesita otro reporte, motivo y verificación. Hito admite 0/100 y COMPLETED requiere evidencia y 100 verificado. Etapas agregan fechas y avance ponderado de descendientes sin recibir peso propio. Múltiples proveedores no multiplican el avance.

La huella de cobertura incluye entregables, fechas, jerarquía, clasificación, títulos/hitos compartibles y extras aprobados/enmiendas contractuales, incluida liquidación parcial. Un cambio relevante deja el porcentaje pendiente hasta nueva aprobación. Propuestas incompletas se conservan como borradores y no se aprueban. Los cambios contractuales y de ficha emiten eventos síncronos; scheduling registra otra revisión/auditoría dentro de la misma transacción, preservando cortes sin depender de un job. Calendar y efectos externos permanecen pendientes M6.

SVAR Community instalado renderiza tareas/etapas/hitos y permite preparar cambios de fechas; formularios permiten todas las operaciones sin arrastrar. Ningún modelo del widget se guarda directamente. Meses y controles ATO se presentan en español. [Interceptar acciones](https://docs.svar.dev/react/gantt/api/methods/intercept/) evita que el widget edite avance, identidad o vínculos fuera del formulario.

El portal usa proyecciones propias con allowlists. Solo entrega fichas propias, contrato, cobros, extras, documentos accesibles y resumen verificado. No incluye actores internos, movimientos de caja, proveedor ni plan administrativo. Los hitos se publican únicamente con una revisión válida aprobada. El proveedor consulta sus asignaciones activas y restricciones de inicio resumidas, reporta con evidencia y no verifica ni consulta el cronograma completo.

## Consecuencias y verificación

Máximo 500 tareas conservadas y 2000 vínculos por obra; revisión y reportes acotados en la lectura operativa. La persistencia mantiene todas las revisiones; `/progress-summary?asOf=...` sirve el corte histórico publicado. Sin auto-scheduling ni actualización silenciosa de sucesoras.

No se añaden dependencias de aplicación, servicios ni cambios de stack/storage. Se conservan Drive, PostgreSQL, Keycloak y las versiones anteriores. T-GANTT/T-CONCUR/T-RBAC/T-API/T-AUDIT/T-DB/T-RESTORE/T-UI se concretan en las suites M3 y el navegador; evidencia exacta y límites productivos en la entrega.
