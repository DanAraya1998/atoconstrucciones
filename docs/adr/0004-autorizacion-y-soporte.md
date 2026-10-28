# 0004 — Autorización por recurso y soporte limitado

**Estado:** Implementado sobre recursos sintéticos en M0; adaptación empresarial/personal propuesta para sus hitos. Límites de confidencialidad confirmados por pedido. **Fecha:** 7 de octubre de 2026. **HU:** HU-07/HU-11/HU-12. **BK:** BK-006/BK-013/BK-034. [Matriz y procedimiento](../03-roles-y-permisos.md).

## Contexto y propuesta

ADMIN requiere diagnosticar problemas; finanzas personales y globales se reservan funcionalmente a GERENTE. Deny-by-default con roles+recursos+campos, sin herencia GERENTE de ADMIN. Proponer grant de soporte otorgado por GERENTE, mínimo recurso/campo, solo lectura, reautenticación reciente con contraseña, MFA opcional desactivado, duración acotada, revocación y auditoría por acceso; personal necesita autorización explícita del titular.

## Alternativas y consecuencias

Superusuario funcional permanente expone datos sin necesidad. Prohibir cualquier acceso aun autorizado dificultaría incidentes; diagnóstico anonimizado es primera opción, grant limitado segunda. El control aplica a API; operador que controla DB/runtime/claves requiere separación de infraestructura y auditoría externa, no se protege solo con un rol Java.

## Verificación

T-RBAC/T-SUPPORT/T-PERSONAL: recurso ajeno, grant caducado/revocado, escalada/recuperación de gerente e impersonación se deniegan; grant válido tiene evidencia por petición. Gobierno de sucesión/permisos de operador P-04/P-10 se acuerda antes de acceso productivo.
