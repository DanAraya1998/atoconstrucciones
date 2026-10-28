# 06 — API y OpenAPI

Extensión 0.4.3: categoría principal opcional `supplierCategory` en entrada/salida de cartera y filtro `GET /suppliers?supplierCategory=...`, con enum `SupplierCategory`. Omisión conserva compatibilidad (OTHER al crear, valor vigente al editar). Reprogramar reutiliza `/projects/{projectId}/schedule/changes`, sin modificar fechas exclusivas ni permisos. [Contrato/estados/errores](26-reprogramacion-y-proveedores.md).

Extensión de cuentas 0.4.2 en [M4](openapi/m4.json): `/accounts`, directorio privado `/accounts/management-search`, capacidades, estado propio `/accounts/me` y decisiones `/approve|reject|suspend|resume|activation`. Contratos previos conservados; gerencia decide con contraseña reciente/idempotencia/ETag y CSRF para cookie. Versiones de cartera/obras en aprobación conjunta. [Recorridos, errores y operación](25-altas-y-aprobacion-de-cuentas.md).

[Índice](README.md) · [Permisos](03-roles-y-permisos.md) · [Datos](05-modelo-de-datos.md) · [Finanzas](08-reglas-financieras.md)

Estado: M0–M4 implementados para desarrollo local, con evidencia en [16](16-entrega-m0.md), [17](17-entrega-m1.md), [19](19-entrega-m2.md), [20](20-entrega-m3.md) y [21](21-entrega-m4.md). Aceptación productiva pendiente; M5–M6 siguen planificados.

## Contrato propuesto

API-first: diseñar especificación revisable antes de implementar cada incremento. OpenAPI **3.1.1** implementado, con esquemas independientes de Next.js. [m4.json](openapi/m4.json) conserva las operaciones M0–M3 y añade M4; las rutas de M5–M6 en este capítulo siguen propuestas. [Especificación oficial](https://spec.openapis.org/oas/v3.1.1.html).

`/api/v1` versiona incompatibilidades mayores; DTO y operaciones en inglés, mensajes visibles en español. “API pública para clientes futuros” significa contrato reusable y documentado: solo `/public/*` es anónimo. Los demás recursos requieren autenticación y [RN-Axx](03-roles-y-permisos.md).

- UUID como string, opaco; `code`/`slug` para presentación pública cuando corresponda. No exponer IDs secuenciales ni usarlos como control de acceso.
- `Money = {amount: string, currency: ISO-4217}`. String decimal sin separador de miles, hasta 5 decimales para fuentes fiscales; escala/tipo por campo. Cada respuesta distingue totales brutos/base/impuestos y `asOf`.
- Fecha civil `YYYY-MM-DD`, instante RFC3339 UTC `...Z`. `timeZone=America/Costa_Rica` para reglas/presentación; Calendar envía zona explícita. `task.endExclusive` no es último día visible.
- DTO de creación no acepta `createdBy`, roles, `confirmed`, saldo, scope/titular, avance calculado o precio confirmado; servidor deriva autoridad/valores. Bean Validation para forma y dominio para reglas de estado.
- `GET` seguro; `POST` crea recurso/comando; `PATCH` actualiza allowlist de campos editables; no PATCH genérico de estado a CONFIRMADO/APROBADO. `DELETE` solo borradores sin referencias o vínculos no financieros autorizados; reversión financiera usa comando.
- `201` + `Location` en creación, `200` en comandos con resultado, `204` sin body, `202` en jobs con URL de estado. Errores no se devuelven con `200`.

## Autenticación y transporte

Web: `/auth/login`, callback OIDC y `/auth/logout` gestionados por Spring/proxy, cookie de sesión segura y CSRF; `/api/v1/session` entrega perfil/capacidades mínimas y `/api/v1/session/csrf` entrega token de CSRF para mutaciones. Logout revoca sesión y cache local. Esos endpoints no son un servicio de emisión de tokens para Flutter.

Flutter: bearer de proveedor OIDC, Authorization Code + PKCE; validar firma/issuer/audience/exp/nbf y scopes; rol de negocio se resuelve en backend. OpenAPI documenta cookie + CSRF y bearer como alternativas explícitas; si una petición usa sesión se exige CSRF. No transportar bearer por query string. CORS por allowlist si hay otro origen autorizado; mismo origen web como propuesta inicial.

## Catálogo de recursos y operaciones

Todas las rutas siguientes llevan `/api/v1`. La matriz 03 es la fuente final de autorización, incluso cuando un path aparece bajo `/projects`. Proveedores/clientes reciben DTO mínimos; no cambiar su estructura según un `role` que envíe el cliente.

| Recurso | Operaciones propuestas | Observaciones |
|---|---|---|
| Identidad | `GET /session`; `GET /session/csrf`; `POST /invitations`; `POST /invitations/{id}/accept`; `POST /users/{id}/disable`; `PUT /users/{id}/roles` | Roles privilegiados requieren sucesión/gerente; accept requiere identidad verificada y token de un uso |
| Membresías | `GET/POST /projects/{p}/members`; `POST /projects/{p}/members/{id}/revoke`; `GET/POST /suppliers/{s}/members` | Se validan partes/vigencia; acciones no habilitadas a clientes/proveedores ordinarios |
| Clientes | `GET/POST /clients`; `GET/PATCH /clients/{id}`; `POST /clients/{id}/deactivate` | No baja cascada de obras; no publicar datos de contacto |
| Proveedores | `GET/POST /suppliers`; `GET/PATCH /suppliers/{id}`; `POST /suppliers/{id}/deactivate`; `GET /suppliers/{id}/assignments` | Campos/asignaciones propios en portal; catálogo de categorías |
| Proyectos | `GET/POST /projects`; `GET/PATCH /projects/{p}`; `POST /projects/{p}/activate`, `/suspend`, `/resume`, `/cancel` | Cambios de estado requieren motivo, versión y reglas; ficha de cliente/proveedor por proyección |
| Contrato | `GET /projects/{p}/contract`; `POST /projects/{p}/contract`; `GET/POST /projects/{p}/contract/amendments` | Solo GERENTE formaliza; lectura de cliente autorizada |
| Extra | `GET/POST /projects/{p}/extras`; `GET /projects/{p}/extras/{e}`; `POST .../{e}/revisions`, `/acceptances`, `/approve`, `/reject`, `/start`, `/complete`, `/cancel` | URLs abreviadas heredan `/projects/{p}/extras`; precios versionados, aceptación separada |
| Depósito | `GET/POST /projects/{p}/deposit-reports`; `GET /projects/{p}/deposit-reports/{d}`; `POST .../{d}/review`, `/confirm`, `/reject`, `/reverse` | Confirmación enlaza tesorería; `reverse` afecta hecho vinculado según RN-F09, no solo flag |
| Gastos | `GET/POST /expenses`; `GET/PATCH /expenses/{e}`; `POST /expenses/{e}/submit`, `/validate`, `/reject`, `/reverse`; `GET /projects/{p}/expenses` | Crear puede distribuir varios proyectos, todos autorizados; proveedor solo reporta su asignación, no valida |
| Documentos fiscales | `POST /fiscal-vouchers/imports`; `GET /fiscal-vouchers/{id}`; `GET /fiscal-vouchers/{id}/diagnostics`; `POST /fiscal-vouchers/{id}/allocations` | Import recibe documentId limpio; allocation propone gastos, no paga ni confirma banco |
| Presupuesto/cotizaciones | `GET/POST /projects/{p}/budgets`; `POST /projects/{p}/budgets/{id}/approve`; `GET/POST /projects/{p}/quotes`; `POST .../quotes/{q}/revisions`, `/submit`, `/review`, `/approve`, `/reject`, `/supersede` | Aprobación genera compromiso único; todos los child IDs se verifican contra p |
| Compromisos | `GET /projects/{p}/commitments`; `GET /projects/{p}/commitments/{c}/consumption`; `POST .../{c}/amendments` | Consumo proviene de reconocimiento de costo, no delta arbitrario enviado por portal |
| Tesorería | `GET/POST /treasury/movements`; `POST /treasury/movements/{m}/confirm`, `/reverse`; `POST .../{m}/collection-allocations`, `/payment-allocations`, `/advance-allocations` | Cuentas/consulta completa GERENTE; asistente solo prepara bajo P-03. Commands especializados preservan sumas |
| Indicadores | `GET /projects/{p}/financial-summary`; `GET /projects/{p}/client-summary`; `GET /operations/collection-followups`; `GET /reports/business-summary` | Cliente no recibe costo/margen; asistente no accede a business-summary; incluir moneda/base/corte |
| Planificación | `GET /projects/{p}/schedule`; `POST /projects/{p}/schedule/changes`; `GET /projects/{p}/schedule/tasks`; `POST .../tasks/{t}/progress-reports`; `POST .../progress-reports/{r}/verify` | `changes` valida lote y revisión global; adaptador SVAR no dicta forma de persistencia |
| Avance | `POST /projects/{p}/schedule/progress-baselines`; `POST .../progress-baselines/{b}/approve`; `GET /projects/{p}/progress-summary` | Resumen mínimo y verificado; proveedores solo tareas asignadas por proyección |
| Upload/download | `POST /documents/upload-sessions`; `POST /documents/{d}/complete-upload`; `GET /documents/{d}`; `POST /documents/{d}/download-authorizations`; `POST /documents/{d}/links`; `POST /documents/{d}/sharing` | URLs firmadas breves; subir no activa; ámbito derivado del recurso; sharing solo gerente |
| Cierre | `GET /projects/{p}/closure-checklist`; `POST /projects/{p}/close`; `GET /projects/{p}/closures/{id}`; `POST /projects/{p}/reopen` | Snapshot y checklist bajo lock; close/reopen solo gerente |
| Publicación M4 | `GET/POST /publications`; `PUT /publications/{id}`; `POST .../{id}/publish`, `/withdraw`; preview privado; consentimientos en `/privacy/consents`; `GET /public/projects`, `/public/projects/{slug}`, `/public/assets/{id}`; `POST /public/contact` | API editorial autenticada; API anónima solo contenido aprobado/variantes autorizadas; consentimiento no visible completo al público |
| Personal | `GET/POST /personal/accounts`, `/personal/categories`, `/personal/transactions`; `POST /personal/transactions/{t}/reverse`; `GET /personal/summary`; `POST /personal/exports` | GERENTE titular; no IDs de proyecto; no union con finanzas de empresa |
| Banco | `POST /bank/statement-imports`; `GET /bank/statement-imports/{i}`; `GET /bank/lines`; `GET/POST /bank/reconciliation-proposals`; `POST .../reconciliation-proposals/{r}/approve`, `/reject`; `POST /bank/reconciliation-links/{l}/reverse` | Fase avanzada, GERENTE; no adapter antes de muestras; aprobación no repite tesorería |
| Calendar | `POST /integrations/calendar/connections`; `GET /integrations/calendar/connections`; `POST .../connections/{id}/disconnect`; `POST /projects/{p}/calendar-sync`; `GET /integrations/calendar/sync-status` | OAuth callback independiente; tokens jamás en DTO; sync es job |
| Sistema/soporte | `GET /audit/events`; `POST /support-access-requests`; `POST /support-access-requests/{id}/approve`; `POST /support-grants/{id}/revoke`; `GET /jobs/{id}`; `POST /reports/exports` | Mismo alcance/confidencialidad; job/export se autoriza al pedir, ejecutar y descargar |

La sintaxis `/confirm`, `/approve` representa comandos de dominio revisables; el contrato implementado deberá declarar esquema, permisos, errores e idempotencia por cada uno. No hay endpoint genérico que actualice tablas.

## DTO mínimos y proyecciones

| DTO | Campos relevantes |
|---|---|
| `ProjectCreate` | `clientId`, `location`, `contact`, `startDate`, `estimatedEndDate`, `contractDraft` con número/moneda/importes; sin estado activo |
| `DepositReportCreate` | `money`, `operationDate`, `paymentMethod`, `bankReference?`, `evidenceDocumentId`, `note?` |
| `DepositConfirmCommand` | `existingMovementId?` o propuesta de movimiento, `allocation`, `verificationDocumentId`, `reason`, `expectedVersion`; nunca ambos existentes/nuevo |
| `ExpenseCreate` | fecha/categoría/proveedor, totales desglosados, `voucherId?`, `allocations[]` con obra/partida/extra y montos; no `paid=true` |
| `ExtraRevisionCreate` | alcance, precio cliente/impuestos/moneda, efecto de plazo, `evidenceDocumentIds`; oferta proveedor por vínculo, no precio implícito |
| `FinancialSummary` | moneda/base/corte/version, `initialContract`, `approvedExtraNet`, `currentContract`, `reportedDeposits`, `confirmedCashReceipts`, `appliedCollections`, `collectionDue`, `customerCredit`, `validatedCosts`, `cashPayments`, `availableProjectCash`, `remainingCommitments`, `estimatedContractResult` |
| `ClientSummary` | contrato vigente, extras aprobados, cobros aplicados netos, pendiente/crédito, avance verificado/corte/alcance y fechas informadas; excluye costo, margen y detalles bancarios |
| `SupplierAssignmentSummary` | asignación/tarea, oferta aprobada propia, reportado/validado/pagado/anticipo/remanente, fechas y documentos compartidos; sin contrato cliente |
| `ScheduleChangeSet` | `baseRevision`, operaciones tipadas de task/dependency/parent, IDs temporales mapeados a UUID por servidor; no payload SVAR persistido sin validar |
| `Problem` | `type`, `title`, `status`, `detail`, `instance`, `code`, `traceId`, `errors[]` opcional, `currentVersion?` sin información privada |

## Ejemplos sintéticos

Crear reporte en `POST /api/v1/projects/00000000-0000-4000-8000-000000000001/deposit-reports`, sesión + CSRF o bearer, `Idempotency-Key` único por intención:

```json
{
  "money": {"amount": "250000.00", "currency": "CRC"},
  "operationDate": "2026-10-07",
  "paymentMethod": "BANK_TRANSFER",
  "bankReference": "REFERENCIA-DE-EJEMPLO",
  "evidenceDocumentId": "00000000-0000-4000-8000-000000000002"
}
```

Respuesta `201`, `Location` del reporte y `ETag: "1"`:

```json
{
  "id": "00000000-0000-4000-8000-000000000003",
  "projectId": "00000000-0000-4000-8000-000000000001",
  "money": {"amount": "250000.00", "currency": "CRC"},
  "status": "REPORTED",
  "version": 1,
  "createdAt": "2026-10-08T03:00:00Z",
  "affectsConfirmedCollections": false
}
```

Resumen del cliente (los demás importes son deliberadamente ausentes):

```json
{
  "currency": "CRC",
  "asOf": "2026-10-08T03:01:00Z",
  "currentContractAmount": "11000000.00",
  "netAppliedCollections": "4000000.00",
  "collectionDue": "7000000.00",
  "customerCredit": "0.00",
  "progress": {"status": "VERIFIED", "percent": "50.00", "baselineRevision": 1,
    "verifiedAt": "2026-10-08T03:00:00Z"},
  "reportedUnconfirmedAmount": "250000.00"
}
```

## Errores y control de concurrencia

`application/problem+json` siguiendo [Problem Details RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html); extensiones propias documentadas. `type` será URI estable bajo dominio de documentación que se decida; mientras tanto `urn:ato:problem:...` permite ejemplos sin inventar dominio de producción.

```json
{
  "type": "urn:ato:problem:schedule-conflict",
  "title": "El cronograma cambió",
  "status": 412,
  "detail": "Revise la versión actual antes de guardar los cambios.",
  "instance": "/api/v1/projects/00000000-0000-4000-8000-000000000001/schedule/changes",
  "code": "VERSION_MISMATCH",
  "traceId": "ejemplo-sin-datos-personales",
  "currentVersion": 8
}
```

| HTTP | Casos / códigos de aplicación |
|---|---|
| 400 | JSON/formato inválido, filtros no admitidos: `MALFORMED_REQUEST` |
| 401 | Sesión/token faltante/expirado: `UNAUTHENTICATED` |
| 403 / 404 | Acción denegada o recurso ajeno según RN-A02; sin stacktrace ni información de existencia |
| 409 | Transición incompatible, duplicado confirmado o reutilizar idempotency key con otro body: `INVALID_STATE`, `DUPLICATE_FACT`, `IDEMPOTENCY_CONFLICT` |
| 412 / 428 | `If-Match` obsoleto/faltante en update requerido: `VERSION_MISMATCH`, `PRECONDITION_REQUIRED` |
| 413 / 415 | Archivo demasiado grande/tipo no permitido |
| 422 | Forma válida pero regla de dominio incumplida: `ALLOCATION_EXCEEDS_AMOUNT`, `CIRCULAR_DEPENDENCY`, `EXCHANGE_RATE_REQUIRED`, `VOUCHER_VALIDATION_FAILED`, `CLOSURE_BLOCKED`; `errors` con JSON Pointer y mensaje |
| 429 | Rate limiting, `Retry-After` |
| 503 | Dependencia temporalmente no disponible; job async conserva estado; sin afirmar confirmación externa |

`GET` mutable entrega ETag. `PATCH` y comandos críticos llevan `If-Match` de agregado y/o `baseRevision` de schedule; ambos deben coincidir si se incluyen. Idempotencia se evalúa antes del estado/precondición para devolver la respuesta de una intención ya completada, después de comprobar identidad/alcance vigente. Las operaciones nuevas siempre validan la versión.

## Idempotencia, listados y evolución

`Idempotency-Key` obligatorio en creación de depósitos/gastos y comandos financieros, aprobación de extras/cotizaciones, cierre, conciliación y jobs externos. Clave acotada y scoped por sujeto/ámbito/operación; hash canónico de la petición, resultado y unique lock transaccional. Propuesta retener respuesta al menos 7 días configurable; unicidad del hecho financiero es permanente. Dos solicitudes simultáneas reciben el mismo resultado o `409 IN_PROGRESS` reintentable, sin doble ejecución. Clientes reintentan POST únicamente con la misma key y payload; nunca generarla de nuevo después de un timeout.

Listados: `page=0&size=25`, máximo propuesto 100; sort por allowlist `sort=operationDate,desc` con `id` de desempate; filtros tipados y rango limitado, siempre después del alcance autorizado. Respuesta `{items,page,size,totalElements}`; no total de registros fuera del ámbito. Para feeds/banco/auditoría grandes se podrá añadir cursor estable bajo contrato distinto. Exportación es job con límite y permiso propio, no paginación ilimitada disfrazada.

Cambios compatibles agregan campos opcionales y nuevas operaciones; no renombrar enum, quitar campos o modificar significado de monto/fecha dentro de v1. Cambios incompatibles van a v2 con convivencia/deprecación acordadas y migración de clientes. DTO de consumidores generados desde OpenAPI en TypeScript y Dart; seleccionar generador mantenido/licencia en BK-007/BK-043, ensayar fechas/strings monetarios/nullable/enums y no asumir precisión de `double` en Dart. El esquema canónico se revisa, y CI compara implementación documentada con contrato y bloquea breaking changes. Los clientes generados no reimplementan la autoridad financiera.

## Contrato ejecutable M0 + M1

[OpenAPI M1](openapi/m1.json) conserva el baseline M0. El contrato vigente servido y generado es [M4](openapi/m4.json); M0–M3 se conservan como baselines. Nuevas rutas `/clients`, `/suppliers`, `/projects` y membresías; contrato bajo obra, `/contract/formalize`, `/contract/amendments`; extras/revisions/acceptances/approve/cancel/progress; documentos/upload-sessions/content/complete-upload/sharing/links; privacy/notices/consents/requests/retention. Consultar la especificación para DTO completos, roles, errores y parámetros.

CRC y máximo dos decimales en contratos/enmiendas; `net`/`tax` separados, total bruto calculado en Spring. `GET /projects/{projectId}/contract?asOf=<Instant>` deriva enmiendas hasta corte; futuro o corte sin base formalizada falla 422. Fechas contractuales/civiles LocalDate; `asOf`, formalización/escaneo UTC Instant.

Mutaciones críticas exigen Idempotency-Key y, donde cambian revisión, If-Match. Upload binario usa reserva de un uso + Content-Length exacto, CSRF en sesión y SHA-256; no recibe URL ni fileId de Drive para descargar por fuera del portal. Ante respuesta de upload perdida, consultar documento y reintentar el mismo contenido si sigue RESERVED. Descarga GET autorizada por petición devuelve bytes con attachment/no-store; documento sin CLEAN falla 409. 429 para cuota de 1 GiB por obra/10 pendientes por usuario; 503 para Drive/escáner indisponibles, permisos Drive inválidos o referencia anterior pendiente de migración. No hay endpoint público ni publicación de evidencias en M1.

## Contrato implementado M2

[m2.json](openapi/m2.json) es la fuente ejecutable y conserva el baseline M1. Incluye depósitos, cuentas lógicas/movimientos/aplicaciones/transferencias, gastos/revisiones de propuesta, XML fiscal y resúmenes/seguimiento. Las rutas del catálogo general siguen propuestas cuando no aparecen en el JSON. Reversión de cobro opera sobre su movimiento vinculado mediante `/treasury/movements/{id}/reverse`; no existe un endpoint de depósito que cambie solamente su flag.

`PATCH /expenses/{id}` requiere If-Match y clave idempotente, valida obras antiguas/nuevas y conserva las imputaciones anteriores. Listas por obra reciben `page/size`; lectura cliente utiliza un DTO distinto y carece de costos/margen/caja bancaria. [Alcance y errores](19-entrega-m2.md).

## Contrato M3 implementado

[OpenAPI M3](openapi/m3.json) preserva M0–M2. Planificación: GET `/projects/{p}/schedule`, POST `/schedule/changes`, GET `/schedule/tasks` para proveedor asignado, POST de reportes/verificaciones y propuestas/aprobación de pesos bajo schedule. Todos los comandos M3 de planificación requieren If-Match global; changes además baseRevision coincidente. `/progress-summary?asOf=...` devuelve proyección verificada al corte. Cliente/proveedor no reciben el grafo interno.

Portal: GET `/client-projects?page=&size=` y `/projects/{p}/client-portal?depositPage=`. POST bajo portal para deposit-reports, extras y acceptances; aceptación mantiene If-Match de extra. DTO propios permiten operar referencias de trámites/documentos sin IDs internos de cuentas, movimientos, partes o actores. Respuestas sin caché y permisos actuales en cada petición. [Resultado y límites](20-entrega-m3.md).

## Contrato M4 implementado

[OpenAPI M0–M4](openapi/m4.json) preserva M3 y es la fuente de `/api/v1/openapi.json` y del cliente TS. Lecturas públicas `/public/site`, `/public/projects`, `/public/projects/{slug}`, `/public/assets/{id}`, `/public/privacy-notice`; contacto `POST /public/contact` con Origin exacto, aviso vigente y UUID de reintento. Sin cookie/CSRF para ese comando anónimo; todos los comandos editoriales privados siguen cookie+CSRF o bearer. `/publications` y publish/withdraw/edit/preview, `/publication-site`, `/publication-consents`, `/contacts` y delegación/reintento; `/operations/status` técnico. Errors 413/415/429 limitan el formulario; 409 aviso obsoleto/clave distinta, 412/428 control editorial. Respuestas públicas/privadas sin caché. [Entrega](21-entrega-m4.md).

Actualización 0.4.1: `POST /accounts/search`, `POST /projects/{id}/account-candidates` y `GET /clients|suppliers|projects/{id}/member-accounts`, exclusivos de GERENTE. Búsqueda por nombre/correo en JSON, con rol/paginación; nombres y correos no van en URL. Candidatas de obra filtran cartera vigente. DTO legibles se resuelven en lote después de autorizar el recurso. Cookie requiere CSRF incluso en búsquedas POST; respuestas `no-store`. UUID y PUT anteriores se conservan como contratos internos. [ADR 0015](adr/0015-cuentas-y-accesos-sin-identificadores-manuales.md), [flujo y límites](23-cuentas-y-accesos.md).

Creación de obra: `ProjectInput.code` conserva `[A-Z0-9-]{3,30}` y unicidad; un código fuera del patrón devuelve 422 `VALIDATION_FAILED`, y un código existente 409 `DUPLICATE_CODE`. El formulario permite escribir `Prueba_001`, muestra que se guardará como `PRUEBA-001` y normaliza antes del body y de la clave de reintento. No cambia el contrato ni se requiere migración. [UX de creación](07-diseno-ux-ui.md).
