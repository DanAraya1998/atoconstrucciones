# 12 — Pruebas y calidad

Rediseño público (27/10/2026), CA-00.6/BK-025/BK-028: `playwright.landing.config.ts` usa API de presentación sintético y standalone aislados; valida lorem/borrador/fallo/aprobado, móvil, teclado, anclas, Axe y bloqueo del optimizador para assets privados. [Fuentes/resultados y límites](27-rediseno-publico-y-lorem-ipsum.md).

Reprogramación/proveedores (27/10/2026): integración de categorías/cartera/permisos, V10→V11, fechas civiles, 13 flujos de navegador y dos recorridos OIDC/PostgreSQL reales, incluyendo resize. Altura con 16 tareas, diálogo/foco/Escape, móvil/teclado y Axe. Restore de 62 tablas/11 migraciones con cronograma/categoría conservados. [Resultados, fuentes y límites](26-reprogramacion-y-proveedores.md).

Altas/aprobación (27/10/2026): T-AUTH/T-RBAC/T-IDEMP/T-CONCUR/T-AUDIT/T-DB se ejecutan en [integración](../src/test/java/com/daraya/atoconstrucciones/AccountOnboardingIntegrationTests.java), [protocolo](../src/test/java/com/daraya/atoconstrucciones/identity/KeycloakAccountProvisioningTests.java) y [upgrade](../src/test/java/com/daraya/atoconstrucciones/AccountOnboardingMigrationTests.java). T-UI/T-A11Y, errores/reintento/confirmación/móvil/teclado: [UI](../frontend/tests/e2e/onboarding.spec.ts) y [OIDC/SMTP local](../frontend/tests/e2e/onboarding-real.spec.ts). [Resultados y límites](25-altas-y-aprobacion-de-cuentas.md). El ensayo real utiliza el config Playwright de onboarding explícito y servicios aislados.

[Índice](README.md) · [Historias](02-requerimientos-y-historias.md) · [Backlog](13-roadmap-y-backlog.md) · [Operación](14-despliegue-y-operacion.md)

Estado: M0–M4 implementados para desarrollo local, con evidencia en [16](16-entrega-m0.md), [17](17-entrega-m1.md), [19](19-entrega-m2.md), [20](20-entrega-m3.md) y [21](21-entrega-m4.md). Exposición productiva pendiente; M5–M6 siguen planificados.

## Estrategia por riesgo

Backend: pruebas unitarias de dominio financiero/DAG/estados y políticas; integración PostgreSQL real con migraciones/locks/constraints; HTTP/Security para DTO/errores/alcance; contratos OpenAPI; mocks de APIs externas para fallos/idempotencia. Frontend: pruebas de formularios y adaptación de DTO, E2E de flujo con roles, accesibilidad y móvil. No sustituir PostgreSQL por H2 para concluir sobre numeric, constraints o concurrencia.

Herramientas actuales: JUnit gestionado por BOM y PostgreSQL embebido real para integración sin Docker; protocolo Drive HTTP controlado; Playwright, Axe y Lighthouse para navegador/accesibilidad/performance. CI usa también ClamAV y herramientas PostgreSQL en Docker. Vitest sigue propuesto; el test de transporte usa Node. Licencias de referencia: [JUnit EPL-2.0](https://github.com/junit-team/junit-framework/blob/main/LICENSE.md), [requisitos JUnit](https://junit.org/), [Testcontainers MIT](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE), [prerrequisitos Docker](https://docs.docker.com/testcontainers/), [Vitest MIT](https://github.com/vitest-dev/vitest/blob/main/LICENSE), [Playwright Apache-2.0](https://github.com/microsoft/playwright/blob/main/LICENSE), [axe-core MPL-2.0](https://github.com/dequelabs/axe-core/blob/develop/LICENSE). Versiones y revisión de las nuevas herramientas en [ADR 0014](adr/0014-publicacion-contacto-operacion-m4.md); conservar el BOM.

## Catálogo de pruebas trazables

Los IDs agrupan suites/escenarios; no significan tests escritos en esta primera fase.

| ID | Nivel y escenarios obligatorios | Criterio de aprobación |
|---|---|---|
| T-STACK | Build/start JDK 27, Maven wrapper, Boot elegido, ORM/driver/Flyway/OpenAPI; build React/Next/SVAR peers/SSR | Combinación reproducible y con política de soporte aceptada; preview autorizada solo para M0, estabilidad pendiente de liberación; P-01 local resuelto, sin warnings ignorados de peers |
| T-DB | PostgreSQL nuevo/actualización desde versión anterior, FKs/unique/numeric/backfill | Misma integridad, validate ok, ninguna pérdida; restore si cambio no reversible |
| T-AUTH | OIDC callback/state, invitación de un uso, recuperación cuando se configure SMTP, MFA opcional, reautenticación reciente, CSRF/cookie/bearer, expiración/logout | No acceso sin autenticación ni bypass CSRF por modo alternativo; revocación efectiva |
| T-RBAC | Cada celda sensible de matriz; cliente A/B, proveedor A/B, asistente y ADMIN; listas/export/jobs/download/joins/mass assignment | Acceso propio permitido, ajeno denegado, no campos reservados; rol acumulado no equivale a GERENTE |
| T-SUPPORT | Grant sin aprobar, caducado, revocado, campo fuera de scope, intento de escalada/impersonación | Denegación; grant válido solo acción/recurso autorizado; auditoría completa |
| T-API | Schemas/nullable/money strings/fechas, pagination/filter allowlist, Problem Details, ETag/If-Match y compatibilidad | Respuestas validadas contra OpenAPI; sin breaking change inadvertido; Dart/TS parsean sin pérdida |
| T-IDEMP | Doble POST, key+body distinto, timeout/retry, dos requests simultáneos | Un único efecto; mismo resultado por intención; conflicto explícito al reutilizar con body distinto |
| T-AUDIT | Aprobación/cambio/reversión, fallo de audit persist, fallo de envío externo | Mutación crítica y evento son atómicos; logs sin secretos; entrega externa reintentable |
| T-PROJECT | Validaciones, cliente/membresías, estados, cambiar contratante, proyecto cancelado/cerrado | Reglas de HU-01 y denegación de mutación ordinaria inválida |
| T-FIN | Ejemplos A–F de 08, impuestos/base, créditos/ajustes/cortes/rounding y propiedad de particiones | Resultados exactos; no floats ni mezclas de monedas; invariantes tras reversión |
| T-CASH | Reportado no cambia saldo; confirmar/refund/pago/anticipo/aplicar sin nueva caja; facturas compartidas | Caja/aplicación/devengo independientes y sin doble imputación |
| T-CONCUR | Confirmaciones/reversiones/aplicaciones simultáneas y cambios de DAG que juntos crean ciclo | Una o ambas se rechazan de forma correcta; saldo/límite/grafo siempre válido |
| T-XML | Tipos 4.4/bundle, namespace, clave duplicada/divergente, NC/ND, totales/receptor/tasa, XXE/bomb | Diagnósticos correctos; sin red/lectura externa; no costo o pago automático |
| T-FILE | MIME falsificado, path traversal, PDF activo, imagen enorme, scanner caído/infección, cuota y descarga revocada | Cuarentena/bloqueo; limpio y autorizado funciona; variante pública sin original privado |
| T-QUOTE | Versiones/vigencia, aprobación única, consumo, anticipo y exceso; proveedor competidor | Compromiso remanente correcto; no aumenta contrato sin extra; aislamiento |
| T-GANTT | FS fin exclusivo, milestone/summary, self-link/ciclos/cross-project, pesos, progreso propuesto/verificado, extra de alcance | DAG válido, porcentaje reproducible o no calculable explícito; cliente sin Gantt interno |
| T-PERSONAL | Ámbito titular, categorías/cuentas, transferencias, exports/search/logs, personal fuera de empresa | Roles no autorizados denegados; cifras empresariales inalteradas por operación personal |
| T-CLOSE | Checklist bloqueo/excepción, snapshot, lock con aprobación concurrente, reapertura y revisión histórica | Cierre coherente al corte, historial conservado y publicación independiente |
| T-PUBLISH | Consentimiento inválido/válido/retirado, metadatos/rostros/domicilio, fichas de portafolio independientes | Ningún asset sin autorización; retirada en distribución controlada; DTO anónimo seguro |
| T-BANK | Fixtures anonimizadas por adaptador/versión, duplicados débiles/fuertes, parcialidades/matching/corrección | Parser solo soporta formatos probados; matching no aplicado sin gerente; caja no duplicada |
| T-CALENDAR | OAuth revocado/scopes, timeout/create retry, update/cancel, 429/5xx, conflicto remoto, invitación | Un evento/mapping por tarea; sin consentimiento no hay acceso externo; fallos no alteran Gantt |
| T-UI | E2E gerente crea obra/cobra/gasta, cliente reporta/solicita extra, asistente revisa; fase 2 proveedor y personal | Flujo completo móvil/desktop, permisos/estados visibles, error/concurrencia recuperable |
| T-A11Y | Axe + teclado/foco/lector, contraste, reduced motion, agenda/tablas móvil | Objetivo WCAG 2.2 AA verificado en pantallas clave; cero hallazgos graves sin resolución |
| T-PERF | Dataset/control de query/N+1, tablas/gantt paginados, carga pública/móvil/jobs | Presupuestos abajo medidos y ajuste documentado; no solo “se ve rápido” |
| T-PRIVACY | Avisos/evidencias/revocación/retención, cache y logs/exports, fixtures sintéticos | No datos privados en público ni secretos en Git/logs; políticas revisadas por alcance |
| T-SEC | Headers, rate limit/spam, XSS/SSRF, permisos endpoints/system y dependencias | Sin vulnerabilidad crítica explotable abierta; severas revisadas con corrección/decisión concreta |
| T-RESTORE | DB+objetos+claves+manifiesto, fallo/restauración, pérdidas/outbox/ACL | Recuperar hechos/evidencias y medir RPO/RTO; cuenta restaurada no abre datos privados |
| T-DEPLOY | CI builds, staging prod-like, secrets, health, migration y rollback compatible | Release reproducible; no data-loss ni configuración de ejemplo en producción |

## Datos, escenarios y pruebas por incremento

Fixtures sintéticos: gerente/ADMIN/asistente, dos clientes y proveedores, obras activas/suspendidas/cerradas, CRC y conversión controlada, varios comprobantes/NC, pagos parciales/anticipos, tareas/ciclos y documentos limpios/hostiles. Muestras bancarias serán anonimizadas y privadas, no se agregan extractos reales al repositorio. Reloj controlado para cortes/fechas/caducidad; moneda como strings exactos. No inferir correctness con mocks que reproduzcan la implementación.

Invariantes financieros: suma de imputaciones ≤ movimiento, reversión ≤ importe reversible, CV = base+enmiendas efectivas, pagos no nacen por validar gasto, consume-compromiso no agrega costo, mismo ID fiscal no duplica. Generar escenarios adicionales/propiedades cuando aporten seguridad sobre dinero y grafo, sin añadir tests triviales que solo verifiquen setters.

Cada tarea ejecuta las pruebas que afecta, más integración existente. En cambios financieros/autorización/migraciones son obligatorias las suites relevantes incluso si la UI apenas cambia. No repetir toda la batería sin cambios nuevos cuando ya pasó y no quedan preocupaciones. La fase documental revisa enlaces/IDs/dependencias/ejemplos; no dispara Maven/Docker para cambios Markdown.

## Presupuestos de calidad propuestos

Performance a validar en staging, no promesas: dataset inicial 200 obras/10 000 gastos, cronograma de 500 tareas y 10 usuarios concurrentes en hardware documentado; endpoints comunes p95 ≤500 ms sin transferir archivos, command crítico p95 ≤1 s sin integración externa, listas paginadas máximo 100. Medir consultas/N+1 y volumen de DTO; ajustar cifras con uso real.

Web pública: LCP ≤2.5 s, INP ≤200 ms, CLS ≤0.1 como objetivos de experiencia; primer ensayo Lighthouse/lab móvil y luego métricas de campo si se habilitan con privacidad. Privado: formularios y navegación responden sin carga de gráfico bloqueante, y Gantt/lista usable con 500 tareas en dispositivo objetivo. Sin cache compartida de privados para mejorar una métrica.

No imponer cobertura porcentual universal. Cobertura mínima cualitativa: cada regla financiera, transición, autorización sensible y escenario excepcional crítico tiene caso positivo/negativo; cada historia tiene al menos un flujo E2E o prueba de integración completa apropiada a su frontera. Rendimiento/accesibilidad/seguridad no se sustituyen por “100% coverage”.

## Definition of Done y gates

Tarea completa cuando: criterio CA/BK demostrado, permisos y error/conflicto ensayados, migración/versionado cuando aplique, contrato actualizado, sin secretos, observabilidad de la nueva operación, documentación/trazabilidad/ADR consistentes y checks relevantes registrados con resultado real. Un stub de integración no cumple historia de integración; un backlog escrito no cumple sus tareas.

M0: stack compatible/soporte acordado, sesiones/permiso/auditoría/idempotencia, contratos y CI. MVP: flujo completo obra→extra→cobro→gasto→pago→resumen, cliente aislado, XML seguro, avance verificable, público autorizado, restore y pruebas de matriz. Fase 2: proveedores/personal/cierre con aislamiento y snapshots. Avanzado: muestras/licencias/consentimientos y fallos externos probados.

## Suites ejecutables M1

[Integración de negocio](../src/test/java/com/daraya/atoconstrucciones/M1IntegrationTests.java) cubre T-PROJECT/T-RBAC/T-FIN/T-CONCUR/T-IDEMP/T-AUDIT/T-PRIVACY/T-FILE; [infraestructura](../src/test/java/com/daraya/atoconstrucciones/DocumentInfrastructureTests.java), T-STACK/T-FILE/T-RESTORE parcial; [upgrade](../src/test/java/com/daraya/atoconstrucciones/M1MigrationTests.java), T-DB; [navegador](../frontend/tests/e2e/m1.spec.ts), T-UI/T-A11Y/móvil/OIDC real. Infraestructura se activa con ATO_M1_INFRA=1; E2E real M1 con ATO_E2E_M1=1. Una omisión explícita no se cuenta como check ejecutado; [resultados locales finales](17-entrega-m1.md).

## Cambio a Google Drive

BK-011 agrega pruebas de protocolo HTTP/PG para ID durable tras timeout y rollback, retries concurrentes, conflicto sin sobrescritura, lectura de revisión fijada frente a edición manual, hash/tamaño, ACL pública/acceso revocado y rechazo de redirección. Upgrade V4→V5 conserva proveedor y versión S3; nuevas reservas usan Drive. La suite `ATO_DRIVE_LIVE_TESTS=1` necesita credenciales y carpeta de pruebas autorizada, más ClamAV real; no se presenta el fixture HTTP como Drive real. Configuración/evidencia en [18](18-almacenamiento-google-drive.md).

## Suites ejecutables M2

[Integración M2](../src/test/java/com/daraya/atoconstrucciones/M2IntegrationTests.java) cubre T-FIN/T-CASH/T-RBAC/T-IDEMP/T-CONCUR/T-AUDIT/T-DB/T-PERF; [parser](../src/test/java/com/daraya/atoconstrucciones/FiscalParserTests.java), T-XML/T-FILE; [fórmulas](../src/test/java/com/daraya/atoconstrucciones/FinancialMetricsTests.java), ejemplos A/B; [upgrade](../src/test/java/com/daraya/atoconstrucciones/M2MigrationTests.java), V5→V6. [Navegador M2](../frontend/tests/e2e/m2.spec.ts) verifica flujos financieros/XML, cliente, móvil y teclado con OIDC y PostgreSQL reales. El [harness](../src/test/java/com/daraya/atoconstrucciones/M2BrowserHarness.java) es exclusivo de test y usa archivos/antivirus controlados. [Restore de DB](../scripts/verify-m2-restore.py) compara fuentes/hechos/revisiones tras restaurar en otra base. No acredita recuperación de Drive/IdP ni rendimiento en staging. [Resultados](19-entrega-m2.md).

## Incremento M3

[Integración M3](../src/test/java/com/daraya/atoconstrucciones/M3IntegrationTests.java), [upgrade](../src/test/java/com/daraya/atoconstrucciones/M3MigrationTests.java) y [navegador](../frontend/tests/e2e/m3.spec.ts) cubren T-GANTT/T-CONCUR/T-RBAC/T-API/T-AUDIT/T-UI con PostgreSQL real. [Restore aislado](../scripts/verify-m2-restore.py) añade tablas M3. Los resultados ejecutados y los checks opt-in/productivos no acreditados se detallan en [20](20-entrega-m3.md).

## Evidencia M4

[Integración M4](../src/test/java/com/daraya/atoconstrucciones/M4IntegrationTests.java): T-PUBLISH/T-PRIVACY/T-RBAC/T-IDEMP/T-CONCUR/T-AUDIT/T-SEC y alertas. [Upgrade](../src/test/java/com/daraya/atoconstrucciones/M4MigrationTests.java), V7→V8; [copia cifrada](../src/test/java/com/daraya/atoconstrucciones/RecoveryArchiveTests.java), GCM/hash/clave/tamper; [Gmail](../src/test/java/com/daraya/atoconstrucciones/integrations/mail/GmailContactMailTests.java), TLS/headers/contenido mínimo sin red real. [E2E M4](../frontend/tests/e2e/m4.spec.ts), públicas/móvil/teclado/Axe, publicación/retirada, reintento tras pérdida de respuesta. [Restore](../scripts/verify-m2-restore.py) M4 compara DB completa/ACL y archivos/clave sintéticos. [Métricas Lighthouse](../frontend/scripts/measure-public.mjs) son de laboratorio local; TBT no sustituye INP de campo. [Resultados ejecutados y límites](21-entrega-m4.md); no declarar pruebas externas por la existencia de configuraciones.
