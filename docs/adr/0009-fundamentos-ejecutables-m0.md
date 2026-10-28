# 0009 — Fundamentos ejecutables M0

**Estado:** Aceptado como implementación del hito autorizado. **Fecha:** 7 de octubre de 2026. **HU:** HU-11/HU-12, infraestructura transversal. **BK:** BK-001 a BK-009. [Entrega](../16-entrega-m0.md) · [Contrato](../openapi/m0.json).

## Contexto y decisión

M0 debe demostrar autorización, contratos, idempotencia, auditoría y jobs sin adelantar módulos financieros. Se crea `foundation`: recursos sintéticos y constancias locales de ejemplo. Sus membresías y grants no conceden acceso automáticamente a futuros datos de obras/finanzas. Identity/audit/shared/foundation mantienen paquetes por módulo; servicios definen transacciones, controladores no consultan repositorios ajenos.

Se usa JDBC para SQL explícito de permisos, idempotencia, auditoría y jobs (locks, SKIP LOCKED y tokens de lease); JPA permanece disponible para futuros agregados empresariales. Flyway mantiene tablas, sesiones JDBC y triggers de auditoría. No crear capas vacías o interfaces sin necesidad.

OpenAPI 3.1.1 es contrato estático versionado, empaquetado en el JAR y servido a usuarios autenticados. Cliente TS generado con openapi-typescript. Se evita añadir springdoc cuya compatibilidad con esta preview no se probó; CI comprueba estructura y diferencias conservadoras del contrato, y tests HTTP verifican DTO/respuestas. No se presenta como validación semántica exhaustiva.

Tests arrancan PostgreSQL real 18.6 mediante el harness Zonky, sin H2 ni daemon Docker obligatorio. Compose mantiene PostgreSQL/Keycloak para desarrollo y CI E2E. En esta máquina, sin daemon, el ensayo E2E usa binarios aislados de las mismas versiones.

## Consecuencias y alternativas

El recurso de demostración no es una obra y no reemplaza las historias M1. El job muestra efecto único local en la transacción PostgreSQL; efectos remotos futuros necesitarán deduplicación en el destino/adaptador. Un lease no garantiza exactamente una llamada externa.

El frontend usa Webpack soportado por Next tras un fallo del procesador CSS de Turbopack en el entorno. SVAR se carga solo en navegador por su inicialización de stores. ESLint/TypeScript/React Hooks evitan peers incompatibles y una dependencia transitiva de lint sin parche. Lockfile y auditoría quedan verificables; versiones, licencias y resultados en 16.

## Verificación

Integración PG: autorización propio/ajeno, roles, invitación, reautenticación, rollback de auditoría, idempotencia concurrente, ETag, lease/reintentos y migraciones. Navegador: móvil/teclado, Gantt sin errores, portal y OIDC real sin MFA obligatorio. CI remota pendiente del próximo push/PR; no hubo publicación ni commit automático.
