# 20 — Entrega M3

[Índice](README.md) · [Backlog](13-roadmap-y-backlog.md) · [Contrato M0–M3](openapi/m3.json) · [ADR 0013](adr/0013-planificacion-y-portal-m3.md) · [Arranque](../README.md)

El usuario autorizó implementar M3 el 8 de octubre de 2026. BK-022 a BK-024 están implementadas para desarrollo local. Se conserva M0–M2, Java 27/Boot 4.2.0-M2, PostgreSQL y Google Drive. M4–M6 siguen pendientes.

## Capacidades entregadas

| Tarea | Comportamiento | Permisos |
|---|---|---|
| BK-022 | Tareas, etapas agregadas e hitos; jerarquía, FS/lag, asignaciones y vínculo de extra; DAG completo, lote atómico, revisión global, retiro con historia | GERENTE/OPERATIONS editan; proveedor solo recibe tareas asignadas y restricciones resumidas; ADMIN y cliente no consultan el plan |
| BK-023 | Reportes con evidencia limpia, verificación, pesos físicos exactos, exclusiones revisadas, cobertura, revisión de alcance y cortes históricos | GERENTE aprueba/verifica con contraseña reciente; asistente/proveedor reportan. Sin baseline válida, avance pendiente; hitos compartidos solo después de aprobación |
| BK-024 | Selector/ficha propia, contrato, saldo/crédito, depósitos pendientes/confirmados, solicitud/aceptación de extra, upload/download e hitos/avance | Cliente requiere membresías activas de parte y obra; aceptación exige representación. DTO no entrega plan interno, proveedores, costos, actores ni movimientos de caja |

Pantallas: cronograma/avance en `/obras/{id}`, lista de tareas asignadas en la ficha del proveedor, selector `/cliente` y ficha `/cliente/obras/{id}`. La ruta histórica `/obras/{id}` también muestra la proyección de cliente a ese rol. Gantt y formularios usan el mismo lote; la lista/formulario funciona en móvil y con teclado. El borrador local permanece visible después de 412 hasta descartarlo y revisar los datos vigentes.

## Reglas operativas

P-12 utiliza el predeterminado documentado: días calendario, FS y pesos por entregables físicos aprobados. No se asumen feriados, calendarios laborales ni cálculo automático de cantidades. Los porcentajes/ponderaciones viajan como strings exactos; el servidor usa BigDecimal. Hojas `[inicio,fin exclusivo)` con duración positiva; hito de duración cero y avance 0/100 según evidencia. Etapas suman descendientes sin duplicar peso. Retirar conserva la tarea; no puede reaparecer por sobrescribir estado. Reabrir una tarea completada requiere GERENTE, contraseña reciente y motivo.

Reportar no publica progreso. Verificar requiere evidencia limpia y conserva la medición original. Correcciones usan otro reporte, fecha no anterior a la última medición y motivo. Un cambio de fechas, entregables, clasificación o enmiendas contractuales (incluida liquidación parcial) invalida cobertura. Cambios contractuales crean revisión en la misma transacción. Ejemplo 50% y reducción 60%→54% reproducibles; el corte previo permanece intacto.

Pesos administrativos cero exigen exclusión motivada; cada hoja/hito contractual tiene peso positivo y todos suman exactamente 100. Cada extra aprobado necesita entregables ponderados. El extra propuesto puede planificarse internamente, pero no entra al avance contractual. Spring entrega `progressTaskIds` del alcance vigente para que el formulario prepare pesos sin duplicar reglas contractuales. No hay monotonía artificial ni avance por transcurso del tiempo.

Máximo 500 tareas conservadas/2000 dependencias/20 proveedores por tarea. La consulta operativa entrega las últimas 20 propuestas de pesos y 100 reportes; la historia completa permanece en snapshots y tablas inmutables. No hay auto-scheduling, ruta crítica, calendarios de recursos ni funciones PRO. Cambiar fechas estimadas no altera el plazo firmado.

## Contrato y datos

V7 es expansiva y no modifica V1–V6. Agrega Schedule/Task/Assignment/Dependency, Report/Verification, Baseline/Weight/Approval y revisiones inmutables con proyección cliente. If-Match y baseRevision gobiernan Schedule, no versiones individuales. Idempotencia, guard Project→Schedule, validación global, evidencia y auditoría comparten transacción.

[OpenAPI M3](openapi/m3.json) conserva las rutas/esquemas M0–M2. `/api/v1/openapi.json` y el generador TS usan M3. Nuevas rutas de planificación/avance, `/client-projects` y `/projects/{p}/client-portal` con comandos seguros. Lecturas sin caché; `/progress-summary?asOf=...` devuelve snapshot al corte, sin reconstruirlo con datos vivos. Los identificadores de trámites/documentos propios habilitan sus comandos y descargas; no se incluyen identificadores de tareas internas, cuentas o actores.

## Verificación local

Resultados del 8 de octubre de 2026, con recursos sintéticos:

| Comprobación | Evidencia |
|---|---|
| Maven `clean verify` | 82 casos: 78 ejecutados, cero fallos/errores, cuatro opt-in omitidos (Drive/antivirus reales); JAR empaquetado |
| M3 PostgreSQL/HTTP | 11 pruebas: intervalos/FS, hitos, etapas, ciclos largos/jerarquía/self-links/cross-project, atomicidad, dos mutaciones incompatibles y colisión UUID entre obras, replay, cobertura/ejemplo/cortes/extra propuesto/aprobado/liquidación parcial/enmienda, proveedor/cliente/ADMIN/revocación, portal DTO, retiro, reauth/evidencia/cierre/rollback de auditoría |
| Upgrade | V6→V7 conserva identidad, contrato, referencias Drive y cuenta de tesorería; Flyway validate. Suites históricas mantienen sus targets |
| Frontend | Generación de contrato M3, TypeScript estricto, lint, transporte y build Next ejecutados; sin dependencias nuevas |
| Navegador | 11 flujos pasan: fundamentos (3), OIDC (1), M1 (1), financieros/XML M2 (2) y M3 (4); permisos/revocación, móvil/teclado, arrastre FS inválido y 412 con borrador conservado |
| Restore | 31 tablas comparadas por conteo y hash integral tras dump/restore en una base nueva, siete migraciones; datos E2E no vacíos (3 cronogramas, 7 tareas, 2 reportes, verificación, pesos y 10 revisiones) |
| Contrato/documentación | Compatibilidad M2, enlaces/secretos y fallos controlados del checker ejecutados |

El harness desactiva DevTools/restart y verifica que el datasource y el puerto de respaldo coincidan; el ensayo M3 rechaza fuentes sin datos en sus tablas esenciales. Reutiliza servicios de negocio y PostgreSQL/Keycloak reales. Los archivos/antivirus son adaptadores controlados exclusivos de test; no están en el JAR y no acreditan Drive real. Docker no está disponible localmente: Keycloak 26.8.0 se ejecuta desde distribución oficial temporal. CI incluye M3; ejecución remota pendiente. Capturas y dumps quedan excluidos de Git en `frontend/test-results/` y `target/`.

## Reproducir navegador y restore aislados

Con Keycloak local, `.env` y `.m0-local/accounts.txt`, preparar el classpath y ejecutar el harness:

```bash
./mvnw --batch-mode --no-transfer-progress test-compile dependency:build-classpath -Dmdep.outputFile=target/m2-test-classpath.txt
python3 scripts/run-m2-browser.py --milestone m3
```

Crea PostgreSQL temporal y Spring en 9192, sin usar la base empresarial ni Drive real. Con 3000 libre:

```bash
ATO_BACKEND_ORIGIN=http://127.0.0.1:9192 npm run build --prefix frontend
ATO_E2E_M1=1 ATO_E2E_M3=1 ATO_E2E_M2=1 ATO_E2E_KEYCLOAK=1 npm run test:e2e --prefix frontend
python3 scripts/verify-m2-restore.py --milestone m3
```

Restore usa herramientas PostgreSQL locales compatibles. CI añade `--docker` para usar las herramientas del contenedor fijado `postgres:18.6`. Ejecutarlo después del navegador, sin mutaciones simultáneas; conserva la copia en una base nueva. Detener el harness con Ctrl+C y recompilar frontend sin `ATO_BACKEND_ORIGIN` para volver al backend habitual en 8080.

## Límites pendientes

Validación de Drive/cuenta real, antivirus real de esta sesión, políticas definitivas de privacidad, Boot estable y operación productiva mantienen los pendientes M0–M2. M4–M6, publicación, PDF, personal, presupuestos/compromisos, USD, bancos, cierre y Calendar quedan fuera de M3. Los checks de teclado/móvil no constituyen certificación WCAG ni prueba completa con lector de pantalla. No se publicó el sitio ni se ejecutó CI remota.
