# 0007 — SVAR Community y avance ponderado de backend

**Estado:** Implementado para desarrollo M3 con predeterminados conservadores; decisiones concretas en [0013](0013-planificacion-y-portal-m3.md). **Fecha:** 7 de octubre de 2026. **HU:** HU-08/HU-05/HU-09. **BK:** BK-022/BK-023/BK-044. **P-12:** días calendario y pesos físicos en M3; alternativas de calendario/medición pendientes. [Detalle canónico](../09-gantt-y-planificacion.md).

## Contexto y propuesta

Gantt administrativo y avance cliente tienen distinto propósito. Community como renderer/editor; backend conserva DAG, intervalos, revisión global y cálculo por pesos de alcance aprobados sobre hojas/hitos. Cliente recibe resumen verificado, proveedor solo tareas propias. MVP FS/días calendario y sin autoschedule.

## Alternativas y consecuencias

Promedio de tareas/duración transcurrida sesga avance; confiar en componente pierde integridad bajo concurrencia. PRO añade capacidades pero necesita licencia; metodología propia de avance no requiere adoptar su baseline visual ni módulo de recursos. Pesos requieren revisión del negocio y trazabilidad al cambiar alcance.

## Verificación

T-GANTT/T-CONCUR validan DAG global y ejemplo ponderado; T-RBAC resumen cliente mínimo. P-12 define días hábiles/medición; cualquier calendario/autoschedule nuevo necesita pruebas/reglas antes de habilitarse. [Comparación oficial de funciones](https://docs.svar.dev/react/gantt/overview/).
