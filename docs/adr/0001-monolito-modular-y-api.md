# 0001 — Monolito modular con API reutilizable

**Estado:** Aceptado por instrucción del usuario. **Fecha:** 7 de octubre de 2026. **Historias:** HU-00–HU-12. **Tareas:** BK-007, BK-010 y demás módulos. [Arquitectura](../04-arquitectura-tecnica.md) · [Backlog](../13-roadmap-y-backlog.md).

## Contexto y decisión

ATO necesita consistencia de contratos/cobros/costos y varios clientes web/Flutter. El pedido fija monolito modular Spring, frontend desacoplado y REST documentada/versionada. Las reglas de negocio viven en backend y sus DTO no dependen del modelo visual de Next o SVAR.

Se conserva Maven/backend en raíz y frontend futuro en `frontend/` como **organización propuesta** de esa decisión; la separación por módulos se detalla en 04. La propuesta evita mover el arranque actual sin necesidad.

## Alternativas y consecuencias

Microservicios añaden consistencia distribuida/despliegues sin necesidad actual. Backend centrado en Next impediría compartir autoridad de negocio con Flutter y sustituiría stack confirmado. Monolito permite transacciones locales y menor operación, pero exige límites comprobables entre módulos y no convierte la DB en API universal.

## Verificación

T-API valida contrato reusable; T-DB/T-CONCUR integridad local; pruebas de arquitectura impiden repositorios de módulos ajenos y lógica financiera en frontend. Si escala/equipos exigen separar un módulo, nuevo ADR y migración de contratos antes de hacerlo.
