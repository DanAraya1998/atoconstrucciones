# 0002 — Stack Java 27 / Boot 4.2 y preview autorizada en M0

**Estado:** Aceptado por instrucciones del usuario para desarrollo M0. **Fecha:** 7 de octubre de 2026. **Tareas:** BK-001/BK-002. [Evaluación](../04-arquitectura-tecnica.md) · [Entrega](../16-entrega-m0.md).

## Contexto y decisión

El pedido actualizado exige Java 27. La instrucción posterior pidió Boot 4.2.0; Maven Central no resolvió su parent final. Tras explicar la alternativa, el usuario aceptó 4.2.0-M2 y autorizó M0. Se conserva Java 27, Maven 3.9.16 y el BOM del parent 4.2.0-M2 sin overrides transitivos ni repositorios snapshot.

Next/React/TypeScript/Tailwind/shadcn/Motion/Query/SVAR siguen el stack del pedido. Versiones fijadas y licencias en 16, lockfile reproducible, Node 24.21.0 LTS. El spike Gantt usa renderizado cliente; no añade reglas empresariales.

## Consecuencias y alternativas

La milestone permite desarrollar con Java 27 y no acredita estabilidad productiva. P-01 queda resuelta para desarrollo; antes de producción se requiere release estable, revisión de BOM y repetición de pruebas. Bajar Java o cambiar de línea Boot no fue la elección del usuario. La disponibilidad futura de 4.2.0 final debe verificarse cuando se actualice.

## Verificación

`./mvnw --batch-mode --no-transfer-progress -Dmaven.repo.local=/tmp/ato-m0-maven clean verify` pasa con PostgreSQL real 18.6 y genera el JAR. Arranque local con Keycloak, integración ORM/Flyway/JDBC y frontend build/SSR/E2E documentados en 16. [Requisitos oficiales Boot 4.2](https://docs.spring.io/spring-boot/4.2/system-requirements.html).
