# 16 — Entrega M0

[Índice](README.md) · [Backlog](13-roadmap-y-backlog.md) · [Arranque local](../README.md) · [Contrato](openapi/m0.json)

## Alcance y decisiones

El usuario autorizó implementar M0, aceptó Boot **4.2.0-M2** para conservar Java 27 y eligió **Keycloak local** para desarrollo. Después retiró la obligatoriedad de MFA. El flujo predeterminado es contraseña OIDC; MFA queda opcional por configuración. Los permisos y la reautenticación reciente no se eliminan.

M0 implementa fundamentos y una demostración sintética. No crea clientes/proyectos empresariales, contratos, balances, pagos, documentos, Gantt de obras ni una landing comercial final. Las reglas y tablas de negocio continúan en M1–M6. El proveedor/operación de identidad productivo, recuperación por correo y sucesión del gerente quedan pendientes antes de desplegar.

| Tarea | Entrega revisable | Evidencia / alcance |
|---|---|---|
| BK-001 | Boot 4.2.0-M2, Java 27, Maven 3.9.16, JAR ejecutable | Resolución Central, compile/verify, ORM/Flyway/driver y arranque con PostgreSQL 18.6; preview autorizada, no release final |
| BK-002 | Next 16.4.0 / React 19.3.0 / TypeScript 5.9.3, lockfile y SVAR 2.8.0 | Build/lint/typecheck, SSR de páginas y Gantt cliente sin conflicto de peers; reduced motion, móvil y teclado. Node 24.21.0 LTS fijado |
| BK-003 | Compose PostgreSQL/Keycloak fijados, perfiles, secretos generados y guard de producción | `docker compose config --quiet`; arranque real con servicios aislados. No daemon Docker disponible en esta máquina; no se afirma haber arrancado Compose aquí |
| BK-004 | CI fijada por SHA, harness PG real, checks de enlaces/secretos/contratos y navegador | Comandos equivalentes ejecutados localmente, fallos controlados de checks probados. Workflow creado; ejecución remota GitHub pendiente del próximo push/PR |
| BK-005 | Login OIDC+PKCE, sesión JDBC, CSRF, invitaciones de un uso y revocación | Prueba real Keycloak/Spring/Next; firma/issuer/audiencia/expiración, callback falso, logout y cuenta desactivada. MFA no obligatorio; recuperación SMTP no configurada |
| BK-006 | Cinco roles propios, memberships por recurso y soporte acotado | Cliente/proveedor/asistente propio/ajeno, ADMIN sin escalada, grant aprobado/caducado/revocado, lectura auditada. En M0 el recurso es sintético; memberships de obras/partes se incorporan en M1 |
| BK-007 | OpenAPI 3.1.1 estático, DTO allowlist, Problem Details, Money/date, ETag y cliente TS | Spec empaquetada coincide con fuente; DTO inválido/desconocido, 400/401/403/404/409/412/422/428, strings decimales exactos y generación repetible. No dependencia springdoc preview |
| BK-008 | Comando transaccional con auditoría e idempotencia por actor/operación/key | Ocho requests concurrentes producen un efecto, replay estable, body distinto 409, ETag desactualizado 412; fallo audit revierte efecto/key; audit no admite UPDATE/DELETE/TRUNCATE |
| BK-009 | Outbox PG, claim SKIP LOCKED, lease/token, backoff+jitter y DEAD visible | Recuperación tras lease vencido, worker viejo no confirma, efecto único y revocación al ejecutar. Solo handler de constancia sintética; handlers de correo/archivos/Calendar se implementarán con sus hitos |

## Versiones, licencias y mantenimiento

Los BOMs de Boot administran Framework/Security/Session/JPA/Flyway/pgJDBC. Resuelto: Hibernate **7.4.9.Final**, Flyway **12.11.0**, pgJDBC **42.7.13**, Security **7.2.0-M2**, Session **4.2.0-M1**. No se alteraron versiones transitivas del BOM para ocultar incompatibilidades. Boot y su stack preview deberán actualizarse/revalidarse antes de producción. [Soporte Java 27](https://docs.spring.io/spring-boot/4.2/system-requirements.html), [publicación M2 en Central](https://spring.io/blog/2026/09/25/spring-boot-4-2-0-M2-available-now/).

PostgreSQL 18.6, [licencia y soporte](https://www.postgresql.org/support/versioning/). Keycloak 26.8.0, Apache-2.0, [release oficial](https://www.keycloak.org/2026/10/keycloak-2680-released). Harness `io.zonky.test:embedded-postgres:2.2.2`, Apache-2.0, binarios PG **18.6.0**, [fuente y configuración](https://github.com/zonkyio/embedded-postgres). No añade una base de datos embebida a producción.

Frontend: Next/React/Tailwind/Motion/Query/SVAR Community/Radix/shadcn/Table/Recharts bajo las licencias de [04](04-arquitectura-tecnica.md). TanStack Table **9.2.6** y Recharts **3.10.1** quedan seleccionados para posteriores pantallas; no se implementó una tabla/gráfico empresarial en M0. shadcn es código fuente MIT adaptado (`button.tsx`) y configuración de componentes; no un paquete runtime ficticio. SVAR Community MIT no incluye PRO. ESLint **10.12.0**, typescript-eslint **8.71.1**, openapi-typescript **7.13.0**, Playwright **1.64.0** fijados en lockfile; licencias respectivamente MIT, MIT, MIT y Apache-2.0. TypeScript 5.9.3 respeta el peer 5.x del generador OpenAPI.

Webpack es una alternativa oficial soportada en Next; se seleccionó tras un fallo de socket del procesador CSS de Turbopack en este entorno. El lint usa ESLint/TypeScript/React Hooks mantenidos: el conjunto inicial con eslint-config-next arrastraba `braces` sin parche, se retiró esa dependencia. El árbol final no tiene peers inválidos; `npm audit` final reporta cero vulnerabilidades al comprobarse. La evidencia es temporal, no una garantía futura.

## Controles concretos y límites

Roles se consultan en ATO en cada petición; claims del IdP no conceden GERENTE. Cuenta OIDC con correo verificado puede iniciar sesión sin roles, pero solo una invitación válida habilita su rol. Tokens de invitación se guardan por hash y se entregan una vez al gerente para compartirlos; no hay envío de correo simulado presentado como real. Cambiar/desactivar GERENTE está denegado por el API corriente.

Cookie ATOSESSION HttpOnly/SameSite=Lax, Secure fuera de dev. Mutaciones de sesión exigen CSRF nuevo; bearer es stateless y valida issuer/aud/exp. Cookie+bearer se rechaza, sin convertir una sesión web en exenta de CSRF. Reautenticación de los últimos cinco minutos para roles/invitaciones/soporte; `ATO_REQUIRE_MFA=false` por decisión del usuario. Si se activa true, se exige evidencia MFA del IdP y su flujo correspondiente, no un flag enviado por el frontend.

Grant de soporte solo lee `id/name/version` de un recurso sintético, no enumera la empresa, no habilita modificaciones ni exports. Recursos personales/financieros no existen en M0 y no heredan ese grant automáticamente al implementarse. Roles empresariales y gobierno de sucesión productiva siguen los límites de [03](03-roles-y-permisos.md).

Jobs demuestran un efecto local deduplicado en la misma transacción que su confirmación. Para efectos externos futuros, exigir idempotency key al destino/adaptador y compensación: el lease no garantiza exactly-once de una llamada remota. No hay payloads de archivos ni correo en la cola actual. Sin retención/borrado automático de auditoría ni idempotencia hasta acordar política.

CI compara contratos existentes de manera conservadora (rutas, operaciones, schemas y seguridad): cambios incompatibles fallan; no es un motor semántico OpenAPI general. Los checks de secretos detectan patrones conocidos y fixtures; no sustituyen gestión de secretos ni un scanner completo. No se ejecutó CI remoto ni se publicó el sitio.

## Validación ejecutada

Comprobaciones finales locales sobre esta entrega:

- Maven `clean verify`: **17 pruebas, cero fallos/errores/omitidas**, JDK 27, PostgreSQL 18.6 real y dos migraciones Flyway. JAR ejecutable generado y arrancado contra PostgreSQL/Keycloak aislados.
- Node **24.21.0**: ESLint sin warnings, TypeScript estricto y build Next Webpack pasan; páginas prerenderizadas y SVAR solo cliente sin el warning de stores del SSR.
- Transporte TypeScript: **3 pruebas**, precisión monetaria, CSRF/idempotency key y Problem Details.
- Navegador Chromium: **4 pruebas, todas pasan**; móvil/teclado, SVAR/reduced motion, portal protegido y OIDC real **sin MFA obligatorio**, invitación y logout.
- Checks Python de enlaces/contratos/secretos pasan; **3 pruebas** provocan y detectan fallos de enlaces, secreto sintético y contratos incompatibles.
- `docker compose config --quiet`, árbol npm sin peers inválidos y auditoría npm con **0 vulnerabilidades** al verificarse. Compose no pudo arrancarse aquí por ausencia de daemon; pruebas reales usaron binarios aislados.

CI remota no ejecutada, correo/recuperación SMTP no configurados y release estable productiva pendientes. Estos resultados validan fundamentos M0 locales, no las funciones empresariales futuras ni una certificación de seguridad.
