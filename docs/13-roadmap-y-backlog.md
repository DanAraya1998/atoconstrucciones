# 13 — Roadmap y backlog

[Índice](README.md) · [Historias](02-requerimientos-y-historias.md) · [Pruebas](12-pruebas-y-calidad.md) · [Trazabilidad](15-trazabilidad.md)

## Secuencia y alcance propuestos

Sin fechas ni estimaciones artificiales. Cada hito entrega una capacidad demostrable; se puede trabajar en ramas independientes donde el DAG lo permite, pero no se declara un hito terminado con sus prerequisitos incompletos. M0 fue autorizado e implementado; evidencia y límites en [16](16-entrega-m0.md). M1 implementado para desarrollo local; evidencia y límites en [17](17-entrega-m1.md). M2 implementado para desarrollo local; evidencia y límites en [19](19-entrega-m2.md). M3 implementado para desarrollo local; evidencia y límites en [20](20-entrega-m3.md). M4 implementado para desarrollo local; M5–M6 permanecen pendientes. CI remota, SMTP y estabilidad productiva no se consideran verificados.

| Hito | Tareas | Resultado verificable y justificación |
|---|---|---|
| M0 — Fundamentos | BK-001 a BK-009 | Stack compatible (preview autorizada para desarrollo), CI, sesión/roles, contratos/auditoría/idempotencia/jobs. Evita construir dinero/documentos antes de poder protegerlos |
| M1 — Obras, contrato y evidencias | BK-010 a BK-015 | Crear obra con partes/permisos, contrato/extras y archivos privados limpios; flujos de aprobación con evidencia |
| M2 — Operación financiera MVP | BK-016 a BK-021 | Reportar/confirmar cobro, reconocer costo, pagar/anticipar e importar XML sin duplicar hechos; indicadores por obra y global GERENTE |
| M3 — Plan y portal cliente | BK-022 a BK-024 | Cronograma válido, avance explicado y cliente consulta/reporta exclusivamente su obra |
| M4 — Público y entrega MVP | BK-025 a BK-029 | Landing/contacto/galería autorizada, flujos end-to-end y recuperación/operación ensayada. Publicar requerirá release revisable y autorización de despliegue cuando corresponda |
| M5 — Fase 2 empresarial | BK-030 a BK-038 | Cotizaciones/compromisos, portal proveedor/consumos, personal aislado, USD, cierre y reportes PDF |
| M6 — Funcionalidades avanzadas | BK-039 a BK-044 | Conciliación con muestras, Calendar autorizado, contrato Flutter y evaluación Gantt avanzada |

**MVP sugerido:** M0–M4 completos. Incluye las capacidades de HU-00/01/02/03/05/08/11/12, resumen global de HU-07, cartera básica de HU-04 y publicación de HU-10. Portal proveedor/cotizaciones/compromisos completos, finanzas personales, cierre contractual, USD operativo y PDF se entregan en M5. Conciliación y Calendar requieren fuentes externas/consentimiento y por eso quedan en M6. La landing puede desarrollarse con contenido/fixtures aprobados de manera independiente, sin publicar obras financieras reales antes de controles documentales.

En MVP puede no existir presupuesto/compromiso formal: mostrar “sin base presupuestaria” y no una desviación ficticia. El modelo reserva estos vínculos para M5. CRC es capacidad inicial propuesta; P-06 puede adelantar BK-037 si hay contratos USD indispensables desde el lanzamiento, reajustando dependencias/pruebas antes de implementar.

## Primer hito recomendado

**M0 implementado y verificado localmente**, BK-001 a BK-009, con Boot 4.2.0-M2/Java 27 y Keycloak local sin MFA obligatorio. Demuestra recurso sintético protegido, autorización entre usuarios, comando idempotente auditado y job recuperable en PostgreSQL real. La configuración CI está creada; su primera ejecución remota queda pendiente. M1 fue autorizado e implementado con las decisiones y límites de [17](17-entrega-m1.md); M2 también fue implementado: [entrega](19-entrega-m2.md); M3 también está implementado: [entrega](20-entrega-m3.md); M4 está implementado localmente: [entrega y límites](21-entrega-m4.md); el siguiente hito funcional es M5.


Las decisiones P-01 a P-13 están en [01](01-vision-y-alcance.md). Resolver una pregunta no bloqueante más tarde es aceptable; cerrar tarea dependiente de una decisión sin resolverla no lo es. Cualquier cambio de alcance/arquitectura actualiza ADR y trazabilidad.

## Fichas de tareas

Campos por tarea: ID/título (descripción del trabajo), historia, prioridad, dependencias, archivos/módulos previstos, resultado esperado, aceptación, pruebas y estado. Los paths de M0–M4 ya existen; los de negocio M5–M6 siguen previstos; backend por módulo bajo `src/main/java/com/daraya/atoconstrucciones/`, tests bajo `src/test/`, frontend en `frontend/`. Números de migraciones se deciden al implementar, no aquí.

## M0 — Fundamentos

### BK-001 — Validar el conjunto Java 27 / Spring Boot / Maven

- **Historia:** HU-11, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** ninguna tarea previa; inspección y decisiones del plan.
- **Archivos/módulos previstos:** pom.xml, .mvn/wrapper/, docs/04-arquitectura-tecnica.md, docs/adr/.
- **Descripción y resultado esperado:** Validar las versiones fijadas por el usuario, Java 27 + Boot 4.2.0-M2 aceptada: resolución del parent, plugins, ORM, driver, Flyway y generación OpenAPI; resolver P-01 con evidencia de soporte. El parent final inicial no se resolvió; la milestone aceptada sí compila y arranca.
- **Criterios de aceptación:** Parent 4.2.0-M2 resoluble, build y arranque reproducibles en JDK 27; matriz de versiones/licencias y requisitos del conjunto registrada; estabilidad productiva pendiente. No completar por modificar el POM; no downgrade ni sustitución implícita por milestone/snapshot.
- **Pruebas esperadas:** T-STACK, build/arranque e integración PG mínima.

### BK-002 — Evaluar y fijar dependencias frontend

- **Historia:** HU-00, HU-08.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** ninguna tarea previa; inspección y decisiones del plan.
- **Archivos/módulos previstos:** frontend/ futuro, docs/04-arquitectura-tecnica.md, docs/09-gantt-y-planificacion.md.
- **Descripción y resultado esperado:** Preparar proyecto Next/React/TypeScript con Node LTS, Tailwind/shadcn/Motion/Query y spike descartable de SVAR; seleccionar Table/Recharts propuestos.
- **Criterios de aceptación:** Lockfile reproducible; React/Next/SVAR renderizan sin conflicto de peers/hidratación; licencias/versions registradas. No se implementan reglas de negocio en el spike.
- **Pruebas esperadas:** T-STACK, build, typecheck, SSR/hidratación y reduced motion mínimo.

### BK-003 — Preparar configuración y servicios de desarrollo

- **Historia:** HU-11, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-001.
- **Archivos/módulos previstos:** compose.yaml, src/main/resources/, .gitignore, docs/14-despliegue-y-operacion.md.
- **Descripción y resultado esperado:** Crear perfiles/config externa, PostgreSQL fijado y ejemplo sin secretos; definir límites/origins y secretos por entorno.
- **Criterios de aceptación:** Entorno arranca sin valores de ejemplo productivos; secret faltante en producción falla cerrado; no se modifica ni limpia DB ajena.
- **Pruebas esperadas:** T-DEPLOY, T-DB, arranque con configuración válida/inválida.

### BK-004 — Establecer CI y harness de pruebas

- **Historia:** HU-11, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-001, BK-002, BK-003.
- **Archivos/módulos previstos:** .github/workflows/ futuro, src/test/, frontend/ configuración de test.
- **Descripción y resultado esperado:** Crear pipeline de Maven/PG, frontend lint/build y checks de documentación/secretos; conservar evidencia por commit.
- **Criterios de aceptación:** Workflow preparado para tests con PG real, build frontend y checks; equivalentes locales pasan, primera ejecución remota pendiente; falla ante secreto de fixture o enlace roto deliberado; herramientas fijadas y licencia/soporte verificados.
- **Pruebas esperadas:** T-STACK, T-DB, T-DEPLOY; prueba del fallo controlado del pipeline.

### BK-005 — Autenticación OIDC y sesiones web

Evolución visual autorizada 27/10/2026, CA-11.7/BK-028: tema nativo ATO y entrada directa a Keycloak, conservando registro/aprobación y recuperación. [Entrega y aplicación local](28-login-con-identidad-ato.md); dominio productivo pendiente y emisor local conservado.

Evolución autorizada 27/10/2026, junto a BK-006/BK-010: alta directa con cliente de servicio Keycloak; autorregistro OIDC con correo verificado y cuenta pendiente; aprobación con perfil/cartera/obras, rechazo/suspensión/reactivación y cola de activación. CA-11.6, [ADR 0017](adr/0017-altas-y-aprobacion-de-cuentas.md), [entrega/verificación](25-altas-y-aprobacion-de-cuentas.md). No inicia M5/M6 ni publica la aplicación.

- **Historia:** HU-11.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-003, BK-004.
- **Archivos/módulos previstos:** identity, configuración Security, frontend/src/features/auth/.
- **Descripción y resultado esperado:** Seleccionar proveedor P-02 y configurar sesión cookie/CSRF e invitación; MFA opcional y desactivado; separar bearer futuro de login web.
- **Criterios de aceptación:** Login/logout/revocación/invitación de un uso funcionan; no token en localStorage; cookie exige CSRF; sin endpoint de recuperación/impersonación de gerente; recuperación por correo del IdP pendiente de SMTP productivo.
- **Pruebas esperadas:** T-AUTH, T-SEC, E2E de sesión y expiración.

### BK-006 — RBAC, políticas y membresías

- **Historia:** HU-11.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-005.
- **Archivos/módulos previstos:** identity, src/test/identity/, docs/03-roles-y-permisos.md.
- **Descripción y resultado esperado:** Implementar capacidades/resource policy y miembros/grants de soporte sin superusuario funcional; conservar gobierno restrictivo en M0; relaciones empresariales/sucesión P-04 antes de M1/producción.
- **Criterios de aceptación:** Matrices sensibles probadas en listas/IDs/children; ADMIN no se autoasigna GERENTE; grant necesita aprobación/vigencia y no habilita cambio financiero.
- **Pruebas esperadas:** T-RBAC, T-SUPPORT, T-AUTH.

### BK-007 — Contrato API, DTO, errores y cliente TypeScript

- **Historia:** HU-00 a HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-001, BK-006.
- **Archivos/módulos previstos:** API por módulos, docs/openapi/ futuro, frontend/src/lib/api/.
- **Descripción y resultado esperado:** Crear OpenAPI incremental, Problem Details, money/date schemas, filtros/ETag y transporte TS independiente de rutas Next.
- **Criterios de aceptación:** Spec válida con seguridad cookie/CSRF/bearer; cliente parsea money string; 403/404/409/412/422 consistentes; breaking diff falla CI.
- **Pruebas esperadas:** T-API, T-RBAC, contract tests.

### BK-008 — Auditoría, idempotencia y unidad transaccional

- **Historia:** HU-02, HU-03, HU-05, HU-11, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-006, BK-007.
- **Archivos/módulos previstos:** audit, shared/idempotency, src/main/resources/db/migration/ futuro.
- **Descripción y resultado esperado:** Crear infraestructura para comandos críticos, locks/versiones y eventos de auditoría append-only; no ledger contable genérico.
- **Criterios de aceptación:** Retry devuelve mismo resultado; key con payload distinto falla; evento audit y efecto se confirman juntos; error audit revierte cambio.
- **Pruebas esperadas:** T-IDEMP, T-AUDIT, T-CONCUR, T-DB.

### BK-009 — Jobs y outbox durables

- **Historia:** HU-00, HU-09, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M0; [evidencia y límites](16-entrega-m0.md).
- **Dependencias:** BK-008, BK-003.
- **Archivos/módulos previstos:** shared/jobs, integrations, migrations y métricas futuras.
- **Descripción y resultado esperado:** Crear lease/reintentos/estado de jobs para archivos, correo y futuras integraciones, sin llamadas remotas dentro de transacción financiera.
- **Criterios de aceptación:** Caída/reinicio recupera job; dos workers no duplican un efecto; error permanente visible y autorización de job/resultado se conserva.
- **Pruebas esperadas:** T-IDEMP, T-AUDIT, T-DEPLOY.

## M1 — Obras, contrato y evidencias

### BK-010 — Clientes, proveedores y proyectos operativos

Evolución 27/10/2026, CA-04.6/04.7: cartera de proveedores sin obra obligatoria, categoría principal controlada/detalle conservado y filtro paginado autorizado; V11. [Entrega y verificación](26-reprogramacion-y-proveedores.md).

- **Historia:** HU-01, HU-04, HU-11.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M1; [evidencia y límites](17-entrega-m1.md).
- **Dependencias:** BK-006, BK-007, BK-008.
- **Archivos/módulos previstos:** parties, projects, migrations, frontend/src/features/projects/.
- **Descripción y resultado esperado:** Implementar cartera/membresías y ficha de obra con estados, ubicación/contacto y versionado; resolver principal/representantes P-04.
- **Criterios de aceptación:** Crear cliente/proveedor/obra y activar con requisitos; miembros ajenos no acceden; fechas/contactos inválidos rechazados; desactivar preserva historial. [Flujo actualizado](23-cuentas-y-accesos.md): buscar/seleccionar cuenta por nombre/correo, confirmar identidad, invitar desde ficha y revocar desde lista; UUID internos. Directorio solo GERENTE; candidatas de obra con cartera/rol activos; servidor revalida estado/acceso y versión.
- **Pruebas esperadas:** T-PROJECT, T-RBAC, T-DB, T-UI.

Corrección de creación del 13 de octubre: el formulario muestra el formato del código de obra (3–30 caracteres A-Z, números y guiones), convierte minúsculas/guiones bajos al formato canónico y bloquea códigos inválidos con explicación junto al campo. Normaliza antes de calcular la clave de reintento; conserva código y clave al enviar con Enter y reintentar tras un fallo. El API y su unicidad permanecen vigentes. [UX](07-diseno-ux-ui.md), [regresión de navegador](../frontend/tests/e2e/project-creation.spec.ts).

### BK-011 — Google Drive privado y cuarentena/escaneo

- **Historia:** HU-12.
- **Prioridad:** P0. **Estado actual:** adaptador Drive implementado; protocolo y controles verificados localmente, conexión real pendiente. [Cambio de proveedor](18-almacenamiento-google-drive.md).
- **Dependencias:** BK-003, BK-009.
- **Archivos/módulos previstos:** documents/infrastructure, config storage/scanner, compose, ADR-0011.
- **Descripción y resultado esperado:** Sustituir S3 por Drive API v3: credenciales de servidor, carpeta privada, fileId durable, revisión fijada, hash y escáner. Resolver tipo de cuenta/carpeta/custodios de P-11 para el ensayo real.
- **Criterios de aceptación:** Upload no activa evidencia sin escaneo; scanner caído/infección mantiene bloqueo; cuenta app no lista ámbitos ajenos; referencia anterior no se reinterpreta; ensayo real de revisión/copia/restauración en cuenta de prueba antes de producción.
- **Pruebas esperadas:** T-FILE, T-STACK, T-RESTORE.

### BK-012 — Vínculos documentales y descarga autorizada

- **Historia:** HU-02, HU-03, HU-10, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M1; [evidencia y límites](17-entrega-m1.md).
- **Dependencias:** BK-006, BK-011.
- **Archivos/módulos previstos:** documents, migrations, frontend/src/features/documents/.
- **Descripción y resultado esperado:** Implementar upload-session/complete, links/audiencias, streaming autorizado y caducidad de reservas vacías.
- **Criterios de aceptación:** Conocer documentId no habilita descarga; hereda ámbito más restrictivo; objeto incompleto no se enlaza limpio; descarga revalida por petición y no entrega URL bearer.
- **Pruebas esperadas:** T-FILE, T-RBAC, T-PRIVACY.

### BK-013 — Política de privacidad, retención y consentimiento

- **Historia:** HU-00, HU-10, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M1; [evidencia y límites](17-entrega-m1.md).
- **Dependencias:** BK-006, BK-012.
- **Archivos/módulos previstos:** documents/policies, publication/consents, docs/11-seguridad-y-auditoria.md.
- **Descripción y resultado esperado:** Resolver P-10/P-13 de responsable/finalidades/retención; obtener textos oficiales completos actuales y diseñar evidencia versionada por finalidad.
- **Criterios de aceptación:** Controles de avisos/consentimiento implementados; revisión y responsable pendientes según instrucción del usuario; retención por clase/holds documentada; retiro no borra hecho sujeto a conservación; ningún plazo legal inventado.
- **Pruebas esperadas:** T-PRIVACY, T-AUDIT, revisión funcional de políticas.

### BK-014 — Contrato, Money y enmiendas

- **Historia:** HU-01, HU-05, HU-07.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M1; [evidencia y límites](17-entrega-m1.md).
- **Dependencias:** BK-010, BK-008.
- **Archivos/módulos previstos:** contracts, shared/Money, migrations, frontend/src/features/contracts/.
- **Descripción y resultado esperado:** Definir política P-05/P-06 para base/impuestos/CRC y persistir contrato base/enmiendas, importes exactos y fecha de corte.
- **Criterios de aceptación:** CI inmutable después de formalizar, CV derivado de deltas únicos; moneda/escala inválidas rechazadas; cambios concurrentes preservan versión.
- **Pruebas esperadas:** T-FIN, T-CONCUR, T-API, T-DB.

### BK-015 — Extras con aceptación y formalización

- **Historia:** HU-05.
- **Prioridad:** P0. **Estado actual:** implementada y verificada localmente en M1; [evidencia y límites](17-entrega-m1.md).
- **Dependencias:** BK-014, BK-012, BK-013.
- **Archivos/módulos previstos:** contracts/extras, frontend/src/features/extras/.
- **Descripción y resultado esperado:** Resolver P-07 y crear solicitud/revisiones/aceptación/formalización, separando precio cliente de costo proveedor.
- **Criterios de aceptación:** Pendiente no altera CV; aprobación con evidencia crea única enmienda; cancelación conserva costos y genera delta; cliente ajeno no acepta extra.
- **Pruebas esperadas:** T-FIN, T-IDEMP, T-RBAC, T-UI.

## M2 — Operación financiera

### BK-016 — Reportes de depósitos y confirmación

- **Historia:** HU-02.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-014, BK-012, BK-008.
- **Archivos/módulos previstos:** treasury/deposits, migrations, frontend/src/features/deposits/.
- **Descripción y resultado esperado:** Resolver autoridad P-03; implementar reporte, revisión y confirmación contra hecho de tesorería con asignación y reversiones.
- **Criterios de aceptación:** Reportado no cambia deuda; confirmación única con evidencia; dup report se vincula sin caja nueva; saldo/crédito y reversión explicables.
- **Pruebas esperadas:** T-CASH, T-FIN, T-IDEMP, T-CONCUR, T-RBAC.

### BK-017 — Gastos y reconocimiento de costos

- **Historia:** HU-03.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-014, BK-012.
- **Archivos/módulos previstos:** costs, migrations, frontend/src/features/expenses/.
- **Descripción y resultado esperado:** Implementar gasto, categorías, imputaciones por obra/extra e impuestos; validación GERENTE y registro asistente bajo P-03.
- **Criterios de aceptación:** Registrar no paga; validar genera costo exacto; factura distribuida no duplica total; diferencias justificadas, histórico y alcance verificados.
- **Pruebas esperadas:** T-FIN, T-RBAC, T-DB, T-AUDIT.

### BK-018 — Pagos, anticipos y caja

- **Historia:** HU-02, HU-03.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-016, BK-017.
- **Archivos/módulos previstos:** treasury/payments, costs/settlements, migrations, frontend/src/features/payments/.
- **Descripción y resultado esperado:** Crear movimiento confirmado, aplicaciones parciales, anticipo/devolución/reversión y efectivo con evidencia; nunca ejecución bancaria.
- **Criterios de aceptación:** Aplicar anticipo no repite salida; límites por movimiento/obligación se conservan concurrentemente; transferencias no inflan ingresos/costos.
- **Pruebas esperadas:** T-CASH, T-CONCUR, T-IDEMP, T-FIN.

### BK-019 — Indicadores por obra y resumen global restringido

- **Historia:** HU-02, HU-03, HU-07.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-014, BK-015, BK-016, BK-017, BK-018.
- **Archivos/módulos previstos:** reporting, frontend/src/features/reports/, operations/followups.
- **Descripción y resultado esperado:** Crear métricas contractuales/devengo/caja y cola de cobros; global solo GERENTE; bases/presupuesto ausente explícitos.
- **Criterios de aceptación:** Ejemplo A reproducible; cliente sin costos; asistente sin endpoint global/export; anticipo no se reserva dos veces; cada cifra tiene moneda/corte/base.
- **Pruebas esperadas:** T-FIN, T-RBAC, T-API, T-PERF.

### BK-020 — Bundle oficial y parser XML 4.4 seguro

- **Historia:** HU-03, HU-12.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-011, BK-012.
- **Archivos/módulos previstos:** documents/fiscal, recursos XSD futuros, docs/10-documentos-e-integraciones.md.
- **Descripción y resultado esperado:** Obtener XSD/revisión oficiales y hashes, clasificar tipos/namespace, extraer preservando precisión y diagnóstico; no usar schemas remotos.
- **Criterios de aceptación:** Casos válidos por tipo soportado y XXE/bomb/namespace inválido probados; clave repetida/divergente detectada; no rótulo fiscal certificado sin verificación.
- **Pruebas esperadas:** T-XML, T-FILE, T-STACK.

### BK-021 — Imputación fiscal, NC/ND y diferencias

- **Historia:** HU-03.
- **Prioridad:** P0. **Estado:** implementada para desarrollo local; [evidencia y límites](19-entrega-m2.md).
- **Dependencias:** BK-020, BK-017, BK-018.
- **Archivos/módulos previstos:** documents/fiscal, costs/adjustments, frontend/src/features/fiscal/.
- **Descripción y resultado esperado:** Convertir extracción en propuesta de gasto por obras; revisar emisor/receptor/totales y vínculo de NC/ND a original.
- **Criterios de aceptación:** XML no crea caja; crédito reduce costo una vez; pago no cambia hasta devolución; original/PDF/imagen misma factura comparten identidad; diferencias aprobadas.
- **Pruebas esperadas:** T-XML, T-FIN, T-CASH, T-RBAC, T-UI.

## M3 — Plan y cliente

### BK-022 — Cronograma, tareas y DAG persistente

Evolución 27/10/2026, CA-08.7/08.8: reprogramar fechas/duración/desplazamiento por formulario y Gantt con altura por filas/vista ampliada, conservando lote, DAG, FS e historial. [Entrega y verificación](26-reprogramacion-y-proveedores.md).

- **Historia:** HU-08.
- **Prioridad:** P0. **Estado:** implementado para desarrollo local; [evidencia y límites](20-entrega-m3.md).
- **Dependencias:** BK-010, BK-007, BK-008.
- **Archivos/módulos previstos:** scheduling, migrations, frontend/src/features/scheduling/.
- **Descripción y resultado esperado:** Implementar tasks/parents/FS/lag, editor SVAR Community mediante adaptador y revisión global de Schedule.
- **Criterios de aceptación:** Ciclo/fechas/cross-project se rechazan en Spring; lote atómico; fin exclusivo consistente; guardado concurrente no rompe DAG; móvil tiene editor por formulario.
- **Pruebas esperadas:** T-GANTT, T-CONCUR, T-UI.

### BK-023 — Baseline de pesos y avance verificado

- **Historia:** HU-08, HU-05.
- **Prioridad:** P0. **Estado:** implementado para desarrollo local; [evidencia y límites](20-entrega-m3.md).
- **Dependencias:** BK-022, BK-015.
- **Archivos/módulos previstos:** scheduling/progress, reporting/progress, frontend/src/features/progress/.
- **Descripción y resultado esperado:** Resolver P-12 y crear reportes de avance, verificación/pesos, revisiones de alcance y resumen cliente.
- **Criterios de aceptación:** Ejemplo 50% reproducible sin duplicar etapas; pesos incompletos no publican porcentaje; extra revisa baseline y explica reducción; proveedor solo reporta.
- **Pruebas esperadas:** T-GANTT, T-RBAC, T-FIN.

### BK-024 — Portal cliente con flujos completos

- **Historia:** HU-01, HU-02, HU-05, HU-08, HU-12.
- **Prioridad:** P0. **Estado:** implementado para desarrollo local; [evidencia y límites](20-entrega-m3.md).
- **Dependencias:** BK-015, BK-016, BK-019, BK-023, BK-012.
- **Archivos/módulos previstos:** frontend/src/features/client/, proyecciones API de cliente.
- **Descripción y resultado esperado:** Implementar selector/ficha, saldo/extras, reporte depósito/documentos e hitos/avance, sin Gantt interno.
- **Criterios de aceptación:** Cliente A solo sus obras; crea reporte y solicitud, distingue pendiente/confirmado; móvil y teclado completan flujos; no costos/IDs internos reservados.
- **Pruebas esperadas:** T-UI, T-RBAC, T-A11Y, T-API.

## M4 — Público y aceptación MVP

### BK-025 — Sitio público con diseño y SEO

Evolución autorizada 27/10/2026, CA-00.6: rediseño editorial de Inicio/ATO/Galería/Contacto, lorem ipsum de muestra e imágenes conceptuales identificadas; contenido real aprobado y controles de publicación preservados. [Entrega y verificación](27-rediseno-publico-y-lorem-ipsum.md).

- **Historia:** HU-00.
- **Prioridad:** P0. **Estado actual:** implementada/ensayada para desarrollo local; aceptación productiva y límites en [21](21-entrega-m4.md) y [22](22-operacion-m4.md).
- **Dependencias:** BK-002, BK-007, BK-013.
- **Archivos/módulos previstos:** frontend/src/app/ público, publication/public DTO, tokens.
- **Descripción y resultado esperado:** Implementar navegación Inicio/ATO/Proyectos/Contacto/Login y servicios/fichas con identidad provisional revisada P-13.
- **Criterios de aceptación:** Diseño móvil/desktop, imágenes optimizadas y texto aprobado; no estadísticas inventadas ni privadas; reduced motion/metadata/sitemap solo público.
- **Pruebas esperadas:** T-A11Y, T-PERF, T-PUBLISH, T-UI.

### BK-026 — Publicación y retirada de galería

- **Historia:** HU-00, HU-10.
- **Prioridad:** P0. **Estado actual:** implementada/ensayada para desarrollo local; aceptación productiva y límites en [21](21-entrega-m4.md) y [22](22-operacion-m4.md).
- **Dependencias:** BK-025, BK-012, BK-013.
- **Archivos/módulos previstos:** publication, documents/variants, frontend/src/features/publication/.
- **Descripción y resultado esperado:** Crear ficha de obra/portafolio y selección/consentimiento/variantes limpias/aprobación editorial; publicación independiente de cierre.
- **Criterios de aceptación:** Asset sin permiso no se distribuye; variante sin EXIF/privados; retirar actualiza permisos/cache controlable; fichas históricas no fabrican finanzas.
- **Pruebas esperadas:** T-PUBLISH, T-FILE, T-PRIVACY, T-RBAC.

### BK-027 — Contacto público y correo durable

- **Historia:** HU-00, HU-11.
- **Prioridad:** P0. **Estado actual:** implementada/ensayada para desarrollo local; aceptación productiva y límites en [21](21-entrega-m4.md) y [22](22-operacion-m4.md).
- **Dependencias:** BK-025, BK-009, BK-013.
- **Archivos/módulos previstos:** publication/contact, integrations/mail, frontend/src/features/contact/.
- **Descripción y resultado esperado:** Seleccionar proveedor de mail/dominio cuando autorizado y crear formulario/anti-spam/aviso, persistencia y notificación mínima.
- **Criterios de aceptación:** Contacto válido se guarda aunque mail falle; rate limit/errores accesibles; no refleja HTML; estado no promete entrega o lectura; remitente real confirmado.
- **Pruebas esperadas:** T-SEC, T-IDEMP, T-UI, T-PRIVACY.

### BK-028 — Verificación integral y revisión del MVP

- **Historia:** HU-00 a HU-03, HU-05, HU-07, HU-08, HU-11, HU-12; HU-04/HU-10 parcial.
- **Prioridad:** P0. **Estado actual:** implementada/ensayada para desarrollo local; aceptación productiva y límites en [21](21-entrega-m4.md) y [22](22-operacion-m4.md).
- **Dependencias:** BK-021, BK-024, BK-026, BK-027, BK-018, BK-004.
- **Archivos/módulos previstos:** src/test/, frontend/tests/, docs/12-pruebas-y-calidad.md, docs/15-trazabilidad.md.
- **Descripción y resultado esperado:** Ejecutar circuito obra→extra→cobro→costo→pago→resumen y pruebas de roles/archivos/Gantt/UI; medir accesibilidad/performance.
- **Criterios de aceptación:** Criterios de MVP y matriz sensibles demostrados; sin hallazgos críticos abiertos; documentación coincide con contratos reales; tareas no entregadas visibles como fase posterior.
- **Pruebas esperadas:** T-UI, T-RBAC, T-SEC, T-A11Y, T-PERF y suites financieras/documentales.

### BK-029 — Preparar operación recuperable y release revisable

- **Historia:** HU-11, HU-12.
- **Prioridad:** P0. **Estado actual:** implementada/ensayada para desarrollo local; aceptación productiva y límites en [21](21-entrega-m4.md) y [22](22-operacion-m4.md).
- **Dependencias:** BK-028, BK-003, BK-011, BK-009.
- **Archivos/módulos previstos:** infra/ futuro, .github/workflows/, runbooks en docs/, config producción.
- **Descripción y resultado esperado:** Resolver RPO/RTO/hosting P-10, preparar imágenes/staging/migraciones/backups/alertas y ensayar recuperación; no publicar sin autorización de despliegue.
- **Criterios de aceptación:** Restauración DB+objetos+claves y permisos medida; artifact/rollback compatible; health y alertas probadas; release concreta y checklist de evidencia listos.
- **Pruebas esperadas:** T-RESTORE, T-DEPLOY, smoke privado/público.

## M5 — Fase 2

### BK-030 — Presupuestos y captura de cotizaciones

- **Historia:** HU-04.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-010, BK-014.
- **Archivos/módulos previstos:** procurement, migrations, frontend/src/features/quotes/.
- **Descripción y resultado esperado:** Crear partidas/versiones de presupuesto y ofertas proveedor/manual con origen, impuestos/vigencia y revisión.
- **Criterios de aceptación:** Presupuesto aprobado separado de ofertas; versiones no sumadas; proveedor ve propia propuesta; oferta manual conserva procedencia.
- **Pruebas esperadas:** T-QUOTE, T-RBAC, T-FIN, T-UI.

### BK-031 — Compromisos, consumo y sobrecostos

- **Historia:** HU-04, HU-03.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-030, BK-017, BK-018.
- **Archivos/módulos previstos:** procurement/commitments, costs/consumption, reporting.
- **Descripción y resultado esperado:** Aprobar oferta como compromiso, distribuir reconocimiento al compromiso y comparar reportado/validado/pagado/remanente.
- **Criterios de aceptación:** Consumo no duplica GV; anticipos no consumen costo; exceso requiere revisión; sustitución mantiene consumo anterior y enmienda restante.
- **Pruebas esperadas:** T-QUOTE, T-FIN, T-CONCUR, T-IDEMP.

### BK-032 — Portal y asignaciones de proveedor

- **Historia:** HU-04, HU-08.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-031, BK-012, BK-024.
- **Archivos/módulos previstos:** frontend/src/features/supplier/, proyecciones API supplier, scheduling/assignments.
- **Descripción y resultado esperado:** Crear espacio proveedor, ofertas propias, agenda/asignaciones y documentos compartidos; no usar panel PRO de recursos.
- **Criterios de aceptación:** Proveedor A no ve oferta/tarea/costo de B; ve presupuesto/consumo propio y agenda; revocación invalida acceso; flujos móviles con teclado.
- **Pruebas esperadas:** T-RBAC, T-QUOTE, T-UI, T-A11Y.

### BK-033 — Consumos y avances reportados por proveedor

- **Historia:** HU-03, HU-04, HU-08.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-032, BK-017, BK-023.
- **Archivos/módulos previstos:** costs/supplierreports, scheduling/progress, frontend/src/features/supplier/.
- **Descripción y resultado esperado:** Permitir reportes de consumo/evidencias/avance de tareas propias que operación revisa; no autoridad financiera del proveedor.
- **Criterios de aceptación:** Reporte crea pendiente, no costo validado/pago/avance público; evidencia scope correcto; duplicado/corrección preserva historial.
- **Pruebas esperadas:** T-RBAC, T-FIN, T-GANTT, T-UI.

### BK-034 — Modelo y API personales aislados

- **Historia:** HU-07.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-006, BK-008, BK-013, BK-019.
- **Archivos/módulos previstos:** personalfinance, esquema personal futuro, documents/personal.
- **Descripción y resultado esperado:** Crear cuentas/categorías/transacciones/reversiones y políticas del titular; no tablas empresariales con simple bandera personal.
- **Criterios de aceptación:** Solo GERENTE titular consulta/escribe; ninguna consulta/backup operativo sin autorización mezcla ámbitos; transferencia clasificada, no venta/costo.
- **Pruebas esperadas:** T-PERSONAL, T-RBAC, T-PRIVACY, T-DB.

### BK-035 — Espacio UX de finanzas personales

- **Historia:** HU-07.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-034, BK-012.
- **Archivos/módulos previstos:** frontend/src/features/personal/, personalfinance/summary.
- **Descripción y resultado esperado:** Crear navegación separada, registro/summary/filtros y descarga privada; mostrar alcance y moneda sin sumar a empresa.
- **Criterios de aceptación:** Operar ingreso/gasto/reversión personal mantiene indicadores empresariales; roles no gerente denegados también por API/export; cache limpia al salir.
- **Pruebas esperadas:** T-PERSONAL, T-UI, T-A11Y, T-RBAC.

### BK-036 — Cierre, snapshots y reapertura

- **Historia:** HU-10.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-019, BK-021, BK-023, BK-031, BK-012.
- **Archivos/módulos previstos:** projects/closure, reporting/snapshots, frontend/src/features/closure/.
- **Descripción y resultado esperado:** Implementar checklist consistente con locks de obra, excepción explícita, snapshot por moneda/versión y reapertura auditada.
- **Criterios de aceptación:** Cambios concurrentes no escapan checklist; snapshot no cambia al reabrir; gastos/ingresos/compromisos pendientes no se ocultan; cerrar no publica.
- **Pruebas esperadas:** T-CLOSE, T-CONCUR, T-FIN, T-RBAC.

### BK-037 — Soporte USD y conversiones persistidas

- **Historia:** HU-01, HU-02, HU-03, HU-07.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-014, BK-018, BK-019, BK-021.
- **Archivos/módulos previstos:** shared/Money, treasury/fx, reporting, personalfinance integración opcional.
- **Descripción y resultado esperado:** Resolver P-06 con fuente/fecha/dirección y registrar original/tasa/convertido/redondeo; ajustar UI/reportes sin tasa viva retroactiva.
- **Criterios de aceptación:** Ejemplo E y tasas/rounding reproducibles; comisión separada; no consolidación con tasa faltante; historial no cambia al actualizar fuente.
- **Pruebas esperadas:** T-FIN, T-CASH, T-API, T-PERSONAL si afecta ese ámbito.

### BK-038 — Reportes PDF y exports protegidos

- **Historia:** HU-07, HU-10.
- **Prioridad:** P1. **Estado inicial:** pendiente.
- **Dependencias:** BK-036, BK-035, BK-037.
- **Archivos/módulos previstos:** reporting/exports, personalfinance/exports, documents/generated.
- **Descripción y resultado esperado:** Evaluar PDFBox/fonts/JDK 27, generar snapshot y tablas por moneda/ámbito con filtros, revisión de fórmulas y descarga.
- **Criterios de aceptación:** PDF coherente con API/corte; texto legible/plantilla aprobada; personal no accesible a ADMIN/asistente; job revalida al ejecutar y entregar.
- **Pruebas esperadas:** T-STACK, T-FIN, T-RBAC, T-FILE, T-UI.

## M6 — Avanzado

### BK-039 — Adaptadores bancarios con muestras probadas

- **Historia:** HU-06.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-012, BK-018, BK-037.
- **Archivos/módulos previstos:** reconciliation/adapters, fixtures privados anonimizados, docs/10-documentos-e-integraciones.md.
- **Descripción y resultado esperado:** Obtener P-09 y crear adapter por banco/formato real con normalización/diagnósticos/provenance; ningún formato inventado.
- **Criterios de aceptación:** Golden fixtures mapean datos reales anonimizados y errores; documento no soportado no crea transacciones; lote repetido no duplica líneas.
- **Pruebas esperadas:** T-BANK, T-FILE, T-PRIVACY.

### BK-040 — Matching, aprobación y corrección de conciliaciones

- **Historia:** HU-06, HU-07.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-039, BK-019, BK-035.
- **Archivos/módulos previstos:** reconciliation, treasury/links, frontend/src/features/reconciliation/.
- **Descripción y resultado esperado:** Crear propuestas explicables/residuales, clasificación empresarial/personal autorizada y aprobación/reversión de vínculo.
- **Criterios de aceptación:** Un movimiento existente no se recrea; matching parcial sin exceder línea; propuesta no definitiva sin gerente; corrección conserva fuentes/historial.
- **Pruebas esperadas:** T-BANK, T-CASH, T-IDEMP, T-RBAC, T-CONCUR.

### BK-041 — OAuth Google Calendar y consentimiento

- **Historia:** HU-09.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-005, BK-009, BK-023, BK-013.
- **Archivos/módulos previstos:** integrations/calendar/auth, identity/secrets, frontend/src/features/calendar/.
- **Descripción y resultado esperado:** Resolver P-08, registrar app/redirect/scopes autorizados y permitir conectar calendario elegido/revocar sin modificar proveedor.
- **Criterios de aceptación:** Tokens cifrados y ausentes de UI/logs; scope mínimo; desconectar impide jobs posteriores; invitación no presume write al calendario de tercero.
- **Pruebas esperadas:** T-CALENDAR, T-AUTH, T-PRIVACY.

### BK-042 — Sincronización Calendar durable en una vía

- **Historia:** HU-09, HU-08.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-041, BK-023.
- **Archivos/módulos previstos:** integrations/calendar/sync, outbox/mappings, frontend sync status.
- **Descripción y resultado esperado:** Implementar mapping/eventId estable, update/cancel/backoff y estado; aceptar invitaciones únicamente mediante acción autorizada.
- **Criterios de aceptación:** Timeout/retry no duplica evento o invitación; revocación pausa; conflicto remoto visible, no sobrescribe Gantt; fallo externo no revierte plan local.
- **Pruebas esperadas:** T-CALENDAR, T-IDEMP, T-AUDIT.

### BK-043 — Contrato para clientes Flutter Android y Windows

- **Historia:** HU-11 y contratos HU-01 a HU-10 aplicables.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-007, BK-024.
- **Archivos/módulos previstos:** docs/06-api-y-openapi.md, clientes Dart de prueba futuros.
- **Descripción y resultado esperado:** Seleccionar generador Dart mantenido/licencia, validar PKCE, schemas/money/fechas/errors/idempotencia con cliente de contrato; no app completa dentro de esta tarea.
- **Criterios de aceptación:** Cliente Dart consume mismos endpoints sin Next; precisión decimal/nullable/enums correctos; política de auth/compatibilidad documentada.
- **Pruebas esperadas:** T-API, T-AUTH, pruebas de contrato con Dart.

### BK-044 — Evaluar planificación avanzada y licencia SVAR

- **Historia:** HU-08.
- **Prioridad:** P2. **Estado inicial:** pendiente.
- **Dependencias:** BK-022, BK-023, BK-028.
- **Archivos/módulos previstos:** scheduling, frontend scheduling, ADR nuevo, docs/09-gantt-y-planificacion.md.
- **Descripción y resultado esperado:** Validar necesidad de calendarios/ruta crítica/resources/export; comparar PRO con algoritmos propios solo ante necesidad demostrada.
- **Criterios de aceptación:** Matriz de capacidad/costo/licencia/soporte aprobada y spike probado; no suponer feature PRO gratis; nueva regla/calendario documentados antes de implementar.
- **Pruebas esperadas:** T-GANTT, T-STACK, T-PERF; prueba de capacidad seleccionada.

## Control de ejecución

Registrar en cada sesión BK/HU, decisiones resueltas, diff, pruebas realmente ejecutadas y cualquier limitación. Pasar de pendiente a en curso/completa solo con evidencia y criterios; si cambia la estructura/autoridad, actualizar la fuente canónica antes de marcar completa. Si una integración todavía usa stub, describirla como no implementada. No introducir pasos de hosting, pagos, conexión externa o publicación no autorizados por la tarea de planificación.
